Build: ./mvnw -T 1C clean verify
Fast compile: ./mvnw -q -DskipTests compile
Module tests: ./mvnw -q -pl <module> test
Run service: ./mvnw -pl <service-boot> -am spring-boot:run or cd module/patra-*-boot && ../../mvnw spring-boot:run
Bring up local infrastructure: cd docker/compose && docker compose up -d