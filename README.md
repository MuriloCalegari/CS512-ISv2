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

## Deploying to remote Duke VCM

Install docker:
```agsl
sudo apt-get update
sudo apt-get install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# Add the repository to Apt sources:
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo service docker start
sudo systemctl enable docker.service
sudo systemctl enable containerd.service
sudo groupadd docker
sudo usermod -aG docker $USER
```