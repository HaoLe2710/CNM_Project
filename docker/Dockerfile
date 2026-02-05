FROM ubuntu:latest
LABEL authors="LEGION"

ENTRYPOINT ["top", "-b"]

FROM postgres:16-alpine

ENV POSTGRES_DB=chat
ENV POSTGRES_USER=chat
ENV POSTGRES_PASSWORD=chat

# Enable better defaults
RUN echo "shared_preload_libraries='pg_stat_statements'" >> /usr/share/postgresql/postgresql.conf.sample

EXPOSE 5432
