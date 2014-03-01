#!/bin/sh

if [ "$#" -eq 1 ]; then
  echo "Running Splice Derby Build"
else
  echo "You did not enter the passphrase for gpg signing" && exit 1	
fi

mvn clean
mvn -Dgpg.passphrase="$1" install
mvn deploy:deploy-file -Dfile=client/target/derbyclient-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyclient -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=client/target/derbyclient-10.9.1.0.splice-sources.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyclient -Dversion=10.9.1.0.splice -Dclassifier=sources -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_cs/target/derbyLocale_cs-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_cs -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_de_DE/target/derbyLocale_de_DE-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_de_DE -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_es/target/derbyLocale_es-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_es -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_fr/target/derbyLocale_fr-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_fr -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_hu/target/derbyLocale_hu-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_hu -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_it/target/derbyLocale_it-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_it -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_ja_JP/target/derbyLocale_ja_JP-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_ja_JP -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_ko_KR/target/derbyLocale_ko_KR-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_ko_KR -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_pl/target/derbyLocale_pl-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_pl -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_pt_BR/target/derbyLocale_pt_BR-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_pt_BR -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_ru/target/derbyLocale_ru-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_ru -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_zh_CN/target/derbyLocale_zh_CN-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_zh_CN -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=derbyLocale_zh_TW/target/derbyLocale_zh_TW-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyLocale_zh_TW -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=engine/target/derby-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derby -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=engine/target/derby-10.9.1.0.splice-sources.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derby -Dversion=10.9.1.0.splice -Dclassifier=sources -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=net/target/derbynet-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbynet -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=net/target/derbynet-10.9.1.0.splice-sources.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbynet -Dversion=10.9.1.0.splice -Dclassifier=sources -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=run/target/derbyrun-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyrun -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=testing/target/derbyTesting-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbyTesting -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=tools/target/derbytools-10.9.1.0.splice.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbytools -Dversion=10.9.1.0.splice -Dpackaging=jar -DrepositoryId=splicemachine
mvn deploy:deploy-file -Dfile=tools/target/derbytools-10.9.1.0.splice-sources.jar -Durl=scp://nexus.splicemachine.com/usr/local/sonatype-work/nexus/storage/releases/ -DgroupId=org.apache.derby -DartifactId=derbytools -Dversion=10.9.1.0.splice -Dclassifier=sources -Dpackaging=jar -DrepositoryId=splicemachine
