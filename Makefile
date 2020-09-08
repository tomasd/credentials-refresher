pom:
	clojure -Spom

build:
	clojure -A:depstar -m hf.depstar.jar target/credentials-refresher.jar

clean:
	rm -fr target

install: build
	mvn install:install-file -Dfile=target/credentials-refresher.jar -DpomFile=pom.xml