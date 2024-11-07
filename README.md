Setting up

To set up this project on a Mac:
```
brew install docker
brew install openjdk@21

echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
```

Then, to run the application, make sure you have the Docker Daemon running.
You can find it on Spotlight or by running `open /Applications/Docker.app`.

To build and run the application, run the following commands:
```
./gradlew build
docker-compose up
```

Your application is now listening on `localhost:8080`.
To shorten the URL, send a POST request to `localhost:8080/shorten` with a JSON body like this:
```
{
  "url": "https://www.google.com"
}
```