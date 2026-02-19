from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.postgres.operators.postgres import PostgresOperator
from datetime import datetime
import csv

default_args = {
    'owner': 'airflow',
    'start_date': datetime(2024, 12, 1),
}

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

def load_emg_to_clickhouse():
    import csv
    import requests
    # Очистим таблицу
    clear_url = 'http://clickhouse:8123/?user=reporter&password=&query=TRUNCATE%20TABLE%20IF%20EXISTS%20emg_sensor_data'
    resp = requests.post(clear_url)
    print(f"Clear response: {resp.status_code} - {resp.text}")

    CSV_FILE_PATH = 'sample_files/olap.csv'
    url = 'http://clickhouse:8123/?user=reporter&password=&query='
    with open(CSV_FILE_PATH, 'r') as f:
        reader = csv.reader(f)
        next(reader)
        rows = []
        for row in reader:
            user_id, prosthesis_type, muscle_group, signal_frequency, signal_duration, signal_amplitude, signal_time = row
            rows.append(f"{user_id}\t{prosthesis_type}\t{muscle_group}\t{signal_frequency}\t{signal_duration}\t{signal_amplitude}\t{signal_time}")
    if rows:
        data = "\n".join(rows)
        insert_query = f"INSERT INTO emg_sensor_data FORMAT TabSeparated {data}"
        resp = requests.post(url, data=insert_query)
        print(f"Insert response: {resp.status_code} - {resp.text}")
    else:
        print("No data to insert")

def create_clickhouse_tables():
    import requests
    url = 'http://clickhouse:8123/?user=reporter&password=&query='
    queries = [
        "CREATE TABLE IF NOT EXISTS emg_sensor_data (user_id UInt32, prosthesis_type String, muscle_group String, signal_frequency UInt32, signal_duration UInt32, signal_amplitude Float32, signal_time DateTime) ENGINE = MergeTree() ORDER BY (user_id, signal_time)",
        "CREATE TABLE IF NOT EXISTS customers (id UInt32, name String, email String, age Nullable(Int32), gender Nullable(String), country Nullable(String), address Nullable(String), phone Nullable(String), version DateTime) ENGINE = ReplacingMergeTree(version) ORDER BY id",
        "CREATE TABLE IF NOT EXISTS customers_queue (id UInt32, name String, email String, age Nullable(Int32), gender Nullable(String), country Nullable(String), address Nullable(String), phone Nullable(String), _timestamp DateTime) ENGINE = Kafka() SETTINGS kafka_broker_list = 'kafka:9092', kafka_topic_list = 'postgres.public.customers', kafka_group_name = 'clickhouse_consumers', kafka_format = 'JSONEachRow', kafka_row_delimiter = '\n', kafka_skip_broken_messages = 1",
        "CREATE MATERIALIZED VIEW IF NOT EXISTS customers_mv TO customers AS SELECT id, name, email, age, gender, country, address, phone, _timestamp AS version FROM customers_queue",
    ]
    for q in queries:
        requests.post(url, data=q)

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

def load_crm_to_clickhouse():
    import csv
    import requests
    from datetime import datetime

    # Очистим таблицу customers
    clear_url = 'http://clickhouse:8123/?user=reporter&password=&query=TRUNCATE%20TABLE%20IF%20EXISTS%20customers'
    requests.post(clear_url)

    CSV_FILE_PATH = 'sample_files/crm.csv'
    url = 'http://clickhouse:8123/?user=reporter&password=&query='
    rows = []
    with open(CSV_FILE_PATH, 'r') as f:
        reader = csv.reader(f)
        next(reader)  # пропускаем заголовок
        for row in reader:
            version = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            # age может быть пустым -> \N
            age = row[3] if row[3] else '\\N'
            # Кавычки не нужны
            row_str = f"{row[0]}\t{row[1]}\t{row[2]}\t{age}\t{row[4]}\t{row[5]}\t{row[6]}\t{row[7]}\t{version}"
            rows.append(row_str)

    if rows:
        data = "\n".join(rows)
        insert_query = f"INSERT INTO customers FORMAT TabSeparated {data}"
        requests.post(url, data=insert_query)

def create_clickhouse_view():
    import requests
    url = 'http://clickhouse:8123/?user=reporter&password=&query='
    view_query = """
                 CREATE VIEW IF NOT EXISTS customer_telemetry_summary_view AS
                 SELECT
                     c.id AS user_id,
                     c.name,
                     c.email,
                     c.age,
                     c.gender,
                     c.country,
                     groupArray(e.prosthesis_type) AS prosthesis_types,
                     count() AS total_signals,
                     avg(e.signal_frequency) AS avg_signal_frequency,
                     avg(e.signal_duration) AS avg_signal_duration,
                     avg(e.signal_amplitude) AS avg_signal_amplitude,
                     min(e.signal_time) AS min_signal_time,
                     max(e.signal_time) AS max_signal_time,
                     max(e.signal_time) AS last_signal_time
                 FROM customers c
                          LEFT JOIN emg_sensor_data e ON c.id = e.user_id
                 GROUP BY c.id, c.name, c.email, c.age, c.gender, c.country \
                 """
    requests.post(url, data=view_query)

with DAG('csv_to_postgres_dag',
         default_args=default_args,
         schedule_interval='@daily',
         catchup=False) as dag:

    create_olap_table_pg = PostgresOperator(
        task_id='create_olap_table_pg',
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

    generate_olap_queries = PythonOperator(
        task_id='generate_olap_queries',
        python_callable=generate_olap_insert_queries
    )

    run_olap_insert_pg = PostgresOperator(
        task_id='run_olap_insert_pg',
        postgres_conn_id='write_to_postgres',
        sql='sql/insert_queries.sql'
    )

    create_crm_table_pg = PostgresOperator(
        task_id='create_crm_table_pg',
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

    generate_crm_queries = PythonOperator(
        task_id='generate_crm_queries',
        python_callable=generate_crm_insert_queries
    )

    run_crm_insert_pg = PostgresOperator(
        task_id='run_crm_insert_pg',
        postgres_conn_id='write_to_postgres',
        sql='sql/insert_crm_queries.sql'
    )

    create_summary_table_pg = PostgresOperator(
        task_id='create_summary_table_pg',
        postgres_conn_id='write_to_postgres',
        sql="""
            CREATE TABLE IF NOT EXISTS customer_telemetry_summary (
                                                                      user_id INTEGER PRIMARY KEY,
                                                                      name VARCHAR(100),
                email VARCHAR(100),
                age NUMERIC,
                gender VARCHAR(10),
                country VARCHAR(100),
                prosthesis_types TEXT[],
                total_signals INTEGER,
                avg_signal_frequency DECIMAL(10,2),
                avg_signal_duration DECIMAL(10,2),
                avg_signal_amplitude DECIMAL(10,2),
                min_signal_time TIMESTAMP,
                max_signal_time TIMESTAMP,
                last_signal_time TIMESTAMP
                );
            """
    )

    fill_summary_table_pg = PostgresOperator(
        task_id='fill_summary_table_pg',
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

    # Clickhouse задачи
    create_ch_tables = PythonOperator(
        task_id='create_ch_tables',
        python_callable=create_clickhouse_tables
    )

    load_emg_to_ch = PythonOperator(
        task_id='load_emg_to_ch',
        python_callable=load_emg_to_clickhouse
    )

    load_crm_to_ch = PythonOperator(
        task_id='load_crm_to_ch',
        python_callable=load_crm_to_clickhouse
    )

    create_ch_view = PythonOperator(
        task_id='create_ch_view',
        python_callable=create_clickhouse_view
    )

    # Порядок выполнения
    create_olap_table_pg >> generate_olap_queries >> run_olap_insert_pg
    create_crm_table_pg >> generate_crm_queries >> run_crm_insert_pg
    [run_olap_insert_pg, run_crm_insert_pg] >> create_summary_table_pg >> fill_summary_table_pg

    # Clickhouse: сначала создаём таблицы, потом загружаем данные, затем создаём представление
    create_ch_tables >> [load_emg_to_ch, load_crm_to_ch] >> create_ch_view