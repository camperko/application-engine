# Netgrif Application Engine

> Simple but powerful

## MySQL

Create NETGRIF user:
```mysql
CREATE USER 'netgrif_nae'@'localhost' IDENTIFIED BY 'netgrif_nae';
GRANT ALL PRIVILEGES ON * . * TO 'netgrif_nae'@'localhost';
```
Create NAE database
```mysql
CREATE DATABASE nae
  DEFAULT CHARACTER SET utf8
  DEFAULT COLLATE utf8_general_ci;
```


## DOCKER
```
sudo docker build -t netgrif/4.3.0 .
sudo docker image ls
sudo docker run --publish 8000:8080 netgrif/4.3.0
```

## TODO Documentation
- Complete installation guide
  - Project structure
  - How to start
- Migrations explained
- Elasticsearch indexing more explained
- List of all properties to configure backend
- FAQ section (help section)
- Backup and restore
- frontend components
  - schematics
  - services
  - views
- Mail sending
- Component tag in processes (asi skôr petriflow)
- Docker
- Petriflow functions (do petriflow)
- LDAP configuration
- Menu import/export
- Autocomplete inputs (do petriflow)