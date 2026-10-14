# PhoneCare-AI Dedicated Python ML Diagnostic Microservice
FROM python:3.9-slim

WORKDIR /app

# Install build & runtime dependencies
COPY ml/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# Copy dataset, training scripts, model artifacts, and service entrypoint
COPY data/ data/
COPY ml/ ml/

# Ensure non-root security context
RUN useradd -m phonecare && chown -R phonecare:phonecare /app
USER phonecare

ENV PORT=5001
EXPOSE 5001

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD python3 -c "import urllib.request; urllib.request.urlopen('http://localhost:5001/health')" || exit 1

ENTRYPOINT ["python3", "ml/service.py", "--host", "0.0.0.0", "--port", "5001"]
