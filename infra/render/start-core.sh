#!/bin/sh
set -eu

if [ -n "${RENDER_DATABASE_URL:-}" ]; then
    database_authority=${RENDER_DATABASE_URL#*://}
    database_location=${database_authority#*@}
    export AGENTFIT_DB_URL="jdbc:postgresql://${database_location}?stringtype=unspecified"
fi

exec java -jar /app/core-api.jar
