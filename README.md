# Blindsend server

This project is a server for [blindsend](https://github.com/blindnet-io/blindsend), an open source tool for private, end-to-end encrypted file exchange. It provides the REST API for managing file exchange workflow.

## Technologies

### Server

blindsend server is built using [Scala 3](https://scala-lang.org/) and various [typelevel](https://typelevel.org/) libraries:
- [cats-effect](https://typelevel.org/cats-effect/) for flow control
- [http4s](https://http4s.org/) for http server
- [circe](https://circe.github.io/circe/) for JSON
- [skunk](https://tpolecat.github.io/skunk/) for database connection

Other libraries include:
- [logback-classic](https://logback.qos.ch/) and janino for logging
- [google-cloud-storage](https://github.com/googleapis/google-cloud-java) for Google Storage link signing
- [pureconfig](https://pureconfig.github.io/) to load configuration files

### Database

Link information is stored in the [PostgreSQL](https://www.postgresql.org/) database. The server connects to it using the [skunk](https://tpolecat.github.io/skunk/) library.

Database consist of a single table defined as:

```sql
CREATE TABLE public.links (
    id varchar NOT NULL,
    workflow varchar NOT NULL,
    stage int2 NOT NULL,
    salt varchar NULL,
    passwordless bool NULL,
    enc_metadata varchar NULL,
    seed_hash varchar NULL,
    "date" timestamp NOT NULL,
    finished bool NOT NULL,
    num_files int2 NULL,
    sender_pk varchar NULL,
    file_ids _varchar NULL,
    wrapped_requester_sk varchar NULL,
    requester_pk varchar NULL,
    life_expectancy int4 NULL DEFAULT 7,
    CONSTRAINT links_pkey PRIMARY KEY (id)
)
```

### File storage

blindsend stores the encrypted files in the [Cloud Storage](https://cloud.google.com/storage) on the [Google Cloud platform](https://cloud.google.com/).

Files are uploaded from the [blindsend web client](https://github.com/blindnet-io/blindsend-fe) directly to a [Google Storage bucket](https://cloud.google.com/storage/docs/key-terms#buckets). For that purpose, the server creates the [signed links](https://cloud.google.com/storage/docs/access-control/signed-urls) for provided file ids and sends them to the web client. Links are generated for both uploading and downloading files.

#### Cors

To access a [GCP bucket](https://cloud.google.com/storage/docs/key-terms#buckets) from the [web client](https://github.com/blindnet-io/blindsend-fe), we need to configure the [CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS) on the bucket.

**cors.json**
```json
[
  {
    "origin": [ "*" ],
    "method": [ "GET", "POST", "PUT" ],
    "responseHeader": [ "content-type", "content-length", "x-goog-resumable", "x-upload-content-length", "x-goog-content-length-range", "x-goog-custom-time" ],
    "maxAgeSeconds": 3600
  } 
]
```

CORS setting can be set by the [gsutil tool](https://cloud.google.com/storage/docs/gsutil):
```sh
gsutil cors set cors.json gs://bucket-name
```

#### Lifecycle

The [bucket](https://cloud.google.com/storage/docs/key-terms#buckets) can be configured to [delete files after a certain condition is met](https://cloud.google.com/storage/docs/lifecycle).  
We set the [Delete](https://cloud.google.com/storage/docs/lifecycle#delete) action when the file's [custom time](https://cloud.google.com/storage/docs/metadata#custom-time) is reached. Custom time is set by a header [x-goog-custom-time](https://cloud.google.com/storage/docs/xml-api/reference-headers#xgoogcustomtime) sent when uploading a file.  
Currently, custom time is current time + 168 hours (7 days).

**lifecycle.json**
```json
{
  "lifecycle": {
    "rule": [
      {
        "action": { "type": "Delete" },
        "condition": { "daysSinceCustomTime": 0 }
      }
    ]
  }
}
```

Lifecycle setting can be set by the [gsutil tool](https://cloud.google.com/storage/docs/gsutil):
```sh
gsutil lifecycle set lifecycle.json gs://bucket-name
```

## Configuration

To successfully connect to the database and generate valid [signed links](https://cloud.google.com/storage/docs/access-control/signed-urls) for [Google Storage](https://cloud.google.com/storage), a **configuration file** must be created.

The following is the example configuration:

```hocon
storage = {                  # Google Storage configuration
  project = "blindsend",     # GCP project where bucket is set
  bucket = "blindsend-files" # Google Storage bucket for uploading files.
}
db = {                       # PostgreSQL database configuration
  host = "127.0.0.1",        # host IP
  port = 5432,               # port, default value is 5432
  user = "postgres",         # database user, default value is postgres
  database = "postgres",     # name of the database containing links table, default value is postgres
  password = "secret"        # database password
}
```

## Deployment

To build an executable file:

1. Install [sbt](https://www.scala-sbt.org/) and run:
    ```sh
    sbt assembly
    ```
    This command will create a **blindsend.jar** executable file in _target/scala-3.1.0_ folder.
1. Create a configuration file _application.json_.  
1. Create a [GCP service account](https://cloud.google.com/iam/docs/service-accounts) (with permission for [Cloud Storage](https://cloud.google.com/storage)) and [download the keys](https://cloud.google.com/iam/docs/creating-managing-service-account-keys).  
1. Set **GOOGLE_APPLICATION_CREDENTIALS** environment variable to point to the _keys file_.
    ```sh
    export GOOGLE_APPLICATION_CREDENTIALS="/account.json"
    ```

To run the server, make sure the **application.json** configuration file is in the **secrets** directory (**secrets** and **blindsend.jar** are in the same directory) and run:
```sh
java -jar ./blindsend.jar
```
This command will start the server on http://0.0.0.0:9000.

## Current status

blindsend is under development by a team of software engineers at [blindnet.io](https://blindnet.io) and several independent cryptography experts.

## License
See [blindsend](https://github.com/blindnet-io/blindsend).