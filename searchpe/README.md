# Searchpe

Microservicio que expone los datos provenientes del `padrón reducido` de la SUNAT.

## Ejecutar en modo desarrollo

### Iniciar servidor

Puedes ejecutar la aplicación en modo desarrollo con:

```shell script
mvn compile quarkus:dev
```

### UI

Searchpe sirve solo su API. La interfaz es la UI única del repositorio: [ui/](../ui) (ver
`ui/e2e/README.md` para levantarla junto a los backends).

## Desplegar en Minikube

- Instala e inicia una instancia de Minikube
- Create un namespace `openubl`
- Create un PVC para la base de datos
- Despliega Searchpe

```shell
minikube start
kubectl create ns openubl
kubectl create -f src/main/kubernetes/minikube-pvc.yml -n openubl
eval $(minikube -p minikube docker-env)
mvn clean package -Dquarkus.kubernetes.deploy=true -Dquarkus.kubernetes.namespace=openubl -DskipTests
```

Expone Searchpe usando:

```shell
minikube service searchpe -n openubl
```

## Links

- [Documentación](https://project-openubl.github.io)
- [Discusiones](https://github.com/project-openubl/xhandler-service/discussions)

## License

- [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
