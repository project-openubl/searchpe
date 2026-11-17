-- searchpe's own database and login (searchpe/src/main/resources/application.properties)
CREATE ROLE searchpe LOGIN PASSWORD 'searchpe';
CREATE DATABASE searchpe OWNER searchpe;
