# Сдача проектной работы 9 спринта
## Задание 1. Повышение безопасности системы

### Задача 1. Предложите архитектурное решение и доработайте диаграмму C4 для управления учётными данными пользователя.

### AS IS
![BionicPRO_C4_model](Task1/BionicPRO_C4_model.jpg)

### TO BE
![BionicPRO_C4_model_to_be](Task1/BionicPRO_C4_model_to_be.jpg)

### Задача 2. Улучшите безопасность существующего приложения, заменив Code Grant на PKCE. 

Доработка файла realm-export.json, добавить атрибут для clients :

```
      {
        "clientId": "reports-frontend",
        ...
        "attributes": {
          "pkce.code.challenge.method": "S256"
        }
      },
```

Доработать App.tsx, включить добавление code_challenge и code_challenge_method в блок initOptions для keycloak:

```
            initOptions={{
                onLoad: 'check-sso',
                pkceMethod: 'S256',  // <--- Включаем PKCE с SHA-256
                flow: 'standard',
                checkLoginIframe: false,
            }}
```

```shell
# Запускаем контейнер
docker compose up -d 

```

Чтобы зайти в админку keycloak:
```shell
# Зайти в контейнер
docker exec -it  architecture-bionicpro-keycloak-1 bash

# Временно отключить HTTPS
/opt/keycloak/bin/kcadm.sh config credentials \
--server http://localhost:8080 \
--realm master --user admin --password admin

# Для всех realms
/opt/keycloak/bin/kcadm.sh update realms/master -s sslRequired=none
/opt/keycloak/bin/kcadm.sh update realms/reports-realm -s sslRequired=none

exit
```