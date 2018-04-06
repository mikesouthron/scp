package org.southy.scp;

import com.jcraft.jsch.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * SCP Utility Class for Uploading/Downloading files to/from an SCP or SFTP server.
 */
public final class Scp {

    enum Type {
        UPLOAD,
        DOWNLOAD
    }

    private Type type;

    private File file;
    private String location;
    private String hostname;
    private String username;
    private String password;
    private String privateKey;
    private int port = 22;

    private String proxyHost;
    private int proxyPort = 0;

    private boolean strict = true;

    private boolean success = false;
    private String error;

    /**
     * Create an SCP Upload object with a File
     * @param file
     * @param hostname
     * @param username
     * @return
     */
    public static Scp upload(final File file, final String hostname, final String username) {
        return new Scp(file, hostname, username, Type.UPLOAD);
    }

    /**
     * Create an SCP Upload object with a filename
     * @param file
     * @param hostname
     * @param username
     * @return
     */
    public static Scp upload(final String file, final String hostname, final String username) {
        return new Scp(new File(file), hostname, username, Type.UPLOAD);
    }

    private Scp(final File file, final String hostname, final String username, final Type type) {
        this.file = file;
        this.hostname = hostname;
        this.username = username;
        this.type = type;

        boolean setEnvProxy = false;
        if (System.getenv("http.proxyHost") != null && System.getenv("http.proxyPort") != null) {
            if (System.getenv("http.nonProxyHosts") != null) {
                String nonProxyHosts = System.getenv("http.nonProxyHosts");
                setEnvProxy = !Pattern.compile(nonProxyHosts).matcher(hostname).matches();
            } else {
                setEnvProxy = true;
            }
        }

        if (setEnvProxy) {
            proxyHost = System.getenv("http.proxyHost");
            proxyPort = Integer.valueOf(System.getenv("http.proxyPort"));
        }
    }

    public Scp location(final String location) {
        this.location = location;
        return this;
    }

    public Scp password(final String password) {
        this.password = password;
        return this;
    }

    public Scp privateKey(final String privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public Scp port(final int port) {
        this.port = port;
        return this;
    }

    public Scp proxy(final String proxyHost, final int proxyPort) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        return this;
    }

    public Scp strictHostKeyChecking(final boolean strict) {
        this.strict = strict;
        return this;
    }

    public boolean success() {
        return success;
    }

    public String error() {
        return error;
    }

    public Scp execute() {
        final JSch jsch = new JSch();
        try {
            Session session = jsch.getSession(this.username, this.hostname, this.port);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", strict ? "yes" : "no");
            session.setConfig(config);

            if (proxyHost != null && proxyPort > 0) {
                Proxy proxy = new ProxyHTTP(proxyHost, proxyPort);
                session.setProxy(proxy);
            }

            if (privateKey != null) {
                jsch.addIdentity(privateKey);
            } else if (password != null) {
                session.setPassword(password);
            }

            session.connect();

            switch (type) {
                case UPLOAD:
                    uploadFile(session);
                    break;
                case DOWNLOAD:
                    //downloadFile(session);
                    break;
            }

            session.disconnect();
        } catch (JSchException | IOException e) {
            success = false;
            error = e.getLocalizedMessage();
        }
        return this;
    }

    private void uploadFile(final Session session) throws IOException, JSchException {
        String fileLocation = file.getName();
        if (location != null) {
            fileLocation = location;
        }
        String command = "scp -t " + fileLocation;
        final Channel channel = session.openChannel("exec");
        ((ChannelExec)channel).setCommand(command);

        OutputStream out = channel.getOutputStream();
        channel.connect();

        command = "C0644 " + file.length() + " ";
        if(fileLocation.lastIndexOf('/')>0){
            command+=fileLocation.substring(fileLocation.lastIndexOf('/')+1);
        }
        else{
            command+=fileLocation;
        }
        command += "\n";

        out.write(command.getBytes());
        out.flush();

        FileInputStream fis = new FileInputStream(file);
        byte[] buf = new byte[4096];
        while (true) {
            int len = fis.read(buf, 0, buf.length);
            if (len <= 0) break;
            out.write(buf, 0, len);
            out.flush();
        }

        buf[0] = 0;
        out.write(buf, 0, 1);
        out.flush();

        out.close();
        fis.close();

        channel.disconnect();
    }

}
