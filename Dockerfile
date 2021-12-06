FROM openjdk
COPY target/scala-3.1.0/blindsend.jar blindsend.jar
CMD cd / && java -jar ./blindsend.jar