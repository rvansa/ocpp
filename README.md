# oc++: interactive oc

Quickstart:

```
mvn package
java -jar target/ocpp-1.0-SNAPSHOT.jar
```

If you just want to try it with prepared bits, you can use docker:

```
docker run -it -u $UID -v $HOME/.kube:/home/jboss/.kube quay.io/rvansa/ocpp
```        
Note: if you use `dnsmasq` to resolve hostnames with some fancier setup, add `--network host -v /etc/resolv.conf:/etc/resolv.conf`

If you don't have `oc` binary on $PATH, set `-Docpp.oc=/path/to/oc`