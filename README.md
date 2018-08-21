# scp

![alt text](https://travis-ci.org/mikesouthron/scp.svg?branch=master "Build Status")

Wrapper for JSch for SCP uploads and downloads

### Maven

```xml
<dependency>
    <groupId>org.southy</groupId>
    <artifactId>scp</artifactId>
    <version>1.0</version>
</dependency>
```

### Usage

#### Download a file

To download a single file with a password and strictHostChecking disabled
```java
File downloadDirectory = new File("download_directory");
Scp scp = Scp
        .download("filename.txt", downloadDirectory, "scp.server", "username")
        .password("password")
        .strictHostKeyChecking(false)
        .execute();
if (scp.success()) {
    //Downloaded file to downloadDirectory/filename.txt
} else {
    //scp.error() contains the exception thrown to cause the failure
    System.our.println(scp.error().getMessage()); 
}
```


#### Upload a file