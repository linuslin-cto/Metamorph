FROM python:3.12-slim

WORKDIR /app
COPY pyproject.toml README.md ./
COPY app ./app
COPY scripts ./scripts
COPY tests ./tests

RUN pip install --no-cache-dir -e ".[dev]"

ENV METAMORPH_EVAL_HOST=0.0.0.0
ENV METAMORPH_EVAL_PORT=8787
ENV METAMORPH_EVAL_WORK=/data/runs

EXPOSE 8787
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8787"]
