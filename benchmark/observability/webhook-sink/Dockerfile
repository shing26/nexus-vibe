FROM python:3.12-alpine

WORKDIR /sink
COPY sink.py .

EXPOSE 9000
CMD ["python", "-u", "sink.py"]
