FROM openjdk:8-alpine

COPY target/uberjar/relembra.jar /relembra/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/relembra/app.jar"]
