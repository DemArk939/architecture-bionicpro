from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.postgres.operators.postgres import PostgresOperator
from datetime import datetime
import csv

# Аргументы по умолчанию
default_args = {
    'owner': 'airflow',
    'start_date': datetime(2024, 12, 1),
}

# Функции генерации SQL-запросов (без изменений)
def escape_sql_string(s):
    return s.replace("'", "''")

def generate_olap_insert_queries():
    CSV_FILE_PATH = 'sample_files/olap.csv'
    with open(CSV_FILE_PATH, 'r') as csvfile:
        csvreader = csv.reader(csvfile)
        insert_queries = []
        is_header = True
        for row in csvreader:
            if is_header:
                is_header = False
                continue
            insert_query = f"INSERT INTO emg_sensor_data (user_id, prosthesis_type, muscle_group, signal_frequency, signal_duration, signal_amplitude, signal_time) VALUES ({row[0]}, '{row[1]}', '{row[2]}', {row[3]}, {row[4]}, {row[5]}, '{row[6]}');"
            insert_queries.append(insert_query)
        with open('./dags/sql/insert_queries.sql', 'w') as f:
            for query in insert_queries:
                f.write(f"{query}\n")

def generate_crm_insert_queries():
    CSV_FILE_PATH = 'sample_files/crm.csv'
    with open(CSV_FILE_PATH, 'r') as csvfile:
        csvreader = csv.reader(csvfile)
        insert_queries = []
        is_header = True
        for row in csvreader:
            if is_header:
                is_header = False
                continue
            name = escape_sql_string(row[1])
            email = escape_sql_string(row[2])
            gender = escape_sql_string(row[4])
            country = escape_sql_string(row[5])
            address = escape_sql_string(row[6])
            phone = escape_sql_string(row[7])
            insert_query = f"""INSERT INTO customers (id, name, email, age, gender, country, address, phone)
                               VALUES ({row[0]}, '{name}', '{email}', {row[3]}, '{gender}', '{country}', '{address}', '{phone}');"""
            insert_queries.append(insert_query)
        with open('./dags/sql/insert_crm_queries.sql', 'w') as f:
            for query in insert_queries:
                f.write(query + "\n")

# Определяем DAG с ежедневным расписанием
with DAG('csv_to_postgres_dag',
         default_args=default_args,
         schedule_interval='@daily',          # Ежедневный запуск
         catchup=False) as dag:

    # Создание таблицы телеметрии
    create_olap_table = PostgresOperator(
        task_id='create_olap_table',
        postgres_conn_id='write_to_postgres',
        sql="""
            DROP TABLE IF EXISTS emg_sensor_data;
            CREATE TABLE IF NOT EXISTS emg_sensor_data (
                                                           user_id INTEGER,
                                                           prosthesis_type TEXT,
                                                           muscle_group TEXT,
                                                           signal_frequency INTEGER,
                                                           signal_duration INTEGER,
                                                           signal_amplitude DECIMAL(5,2),
                signal_time TIMESTAMP
                );
            """
    )

    # Генерация и выполнение вставок в телеметрию
    generate_olap_queries = PythonOperator(
        task_id='generate_olap_queries',
        python_callable=generate_olap_insert_queries
    )

    run_olap_insert_queries = PostgresOperator(
        task_id='run_olap_insert_queries',
        postgres_conn_id='write_to_postgres',
        sql='sql/insert_queries.sql'
    )

    # Создание таблицы клиентов
    create_crm_table = PostgresOperator(
        task_id='create_crm_table',
        postgres_conn_id='write_to_postgres',
        sql="""
            DROP TABLE IF EXISTS customers;
            CREATE TABLE IF NOT EXISTS customers (
                                                     id SERIAL PRIMARY KEY,
                                                     name VARCHAR(100),
                email VARCHAR(100),
                age NUMERIC,
                gender VARCHAR(10),
                country VARCHAR(100),
                address VARCHAR(255),
                phone VARCHAR(25)
                );
            """
    )

    # Генерация и выполнение вставок в CRM
    generate_crm_queries = PythonOperator(
        task_id='generate_crm_queries',
        python_callable=generate_crm_insert_queries
    )

    run_crm_insert_queries = PostgresOperator(
        task_id='run_crm_insert_queries',
        postgres_conn_id='write_to_postgres',
        sql='sql/insert_crm_queries.sql'
    )

    # --- НОВЫЙ БЛОК: витрина данных ---
    create_summary_table = PostgresOperator(
        task_id='create_summary_table',
        postgres_conn_id='write_to_postgres',
        sql="""
            CREATE TABLE IF NOT EXISTS customer_telemetry_summary (
                                                                      user_id INTEGER PRIMARY KEY,
                                                                      name VARCHAR(100),
                email VARCHAR(100),
                age NUMERIC,
                gender VARCHAR(10),
                country VARCHAR(100),
                prosthesis_types TEXT[],               -- список типов протезов
                total_signals INTEGER,                  -- общее количество сигналов
                avg_signal_frequency DECIMAL(10,2),    -- средняя частота
                avg_signal_duration DECIMAL(10,2),      -- средняя длительность
                avg_signal_amplitude DECIMAL(10,2),     -- средняя амплитуда
                min_signal_time TIMESTAMP,              -- первый сигнал
                max_signal_time TIMESTAMP,               -- последний сигнал
                last_signal_time TIMESTAMP               -- дубль для удобства
                );
            """
    )

    fill_summary_table = PostgresOperator(
        task_id='fill_summary_table',
        postgres_conn_id='write_to_postgres',
        sql="""
            TRUNCATE customer_telemetry_summary;
            INSERT INTO customer_telemetry_summary
            SELECT
                c.id AS user_id,
                c.name,
                c.email,
                c.age,
                c.gender,
                c.country,
                ARRAY_AGG(DISTINCT e.prosthesis_type) AS prosthesis_types,
                COUNT(e.*) AS total_signals,
                COALESCE(AVG(e.signal_frequency), 0) AS avg_signal_frequency,
                COALESCE(AVG(e.signal_duration), 0) AS avg_signal_duration,
                COALESCE(AVG(e.signal_amplitude), 0) AS avg_signal_amplitude,
                MIN(e.signal_time) AS min_signal_time,
                MAX(e.signal_time) AS max_signal_time,
                MAX(e.signal_time) AS last_signal_time
            FROM customers c
            LEFT JOIN emg_sensor_data e ON c.id = e.user_id
            GROUP BY c.id, c.name, c.email, c.age, c.gender, c.country;
        """
    )

    # --- Последовательность выполнения ---
    create_olap_table >> generate_olap_queries >> run_olap_insert_queries >> \
    create_crm_table >> generate_crm_queries >> run_crm_insert_queries >> \
    create_summary_table >> fill_summary_table