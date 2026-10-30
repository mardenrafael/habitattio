# Imagem do habitattio (specs/002-dockerize-app). Só instruções comuns ao BuildKit e ao builder
# clássico: o operador constrói com BuildKit, o ImagemContainerTest no clássico (research R3).
# Bases fixadas por digest; atualização em docs/container.md.
ARG BASE_BUILD=eclipse-temurin:25-jdk@sha256:83f4a7fc27df94dcc7b8de04d14cbbba8da14406f921e966969c3b09000aa507
ARG BASE_RUNTIME=gcr.io/distroless/java25-debian13:nonroot@sha256:fc6f4aec634ee433ff16a5d18814181e7928f4be9f434b44b48231e2eb7becee

FROM ${BASE_BUILD} AS build
WORKDIR /build

# Dependências numa camada própria: só é refeita quando o pom.xml ou o wrapper mudam.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

# Sem testes aqui: a suíte completa roda antes, no host.
COPY src src
RUN ./mvnw -B -q -o package -DskipTests
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# Verificador de saúde: a imagem final não tem curl/wget nem shell (research R4).
COPY docker/VerificarSaude.java docker/
RUN javac --release 25 -d verificacao docker/VerificarSaude.java

# Imagem final: só o JRE, usuário nonroot (uid 65532), sem shell.
FROM ${BASE_RUNTIME}
WORKDIR /app
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
COPY --from=build /build/verificacao/ /app/verificacao/
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD ["java", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Xmx16m", "-cp", "/app/verificacao", "VerificarSaude"]

ARG VERSION=dev
ARG REVISION=dev
LABEL org.opencontainers.image.version=${VERSION} \
      org.opencontainers.image.revision=${REVISION}

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
