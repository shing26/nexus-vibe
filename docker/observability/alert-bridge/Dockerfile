FROM python:3.12-alpine

WORKDIR /bridge
COPY alert_bridge.py .

# This bridge is new, so it starts as a non-root user instead of adding a hardening ticket later.
USER nobody

EXPOSE 8080
CMD ["python", "-u", "alert_bridge.py"]
