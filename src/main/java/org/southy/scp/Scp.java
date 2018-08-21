package org.southy.scp;

import com.jcraft.jsch.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.Vector;
import java.util.regex.Pattern;

/**
 * SCP Utility Class for Uploading/Downloading files to/from an SCP or SFTP server.
 * Opinionated wrapper around JSch
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
    private Exception error;

    /**
     * Create an SCP Download object. Filename of file on server, directory is the local directory that the file(s) will be downloaded to.
     * @param filename
     * @param directory
     * @param hostname
     * @param username
     * @return
     */
    public static Scp download(final String filename, final File directory, final String hostname, final String username) {
        return new Scp(directory, hostname, username, Type.DOWNLOAD).location(filename);
    }

    /**
     * Create an SCP Download object. Filename of file on server, directory is the local directory that the file(s) will be downloaded to.
     * @param filename
     * @param directory
     * @param hostname
     * @param username
     * @return
     */
    public static Scp download(final String filename, final String directory, final String hostname, final String username) {
        return new Scp(new File(directory), hostname, username, Type.DOWNLOAD).location(filename);
    }

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

    /**
     * Returns success true or false.
     * If there was a failure the error() will contain the Exception.
     * @return
     */
    public boolean success() {
        return success;
    }

    /**
     * Get the exception if there was an error.
     * @return
     */
    public Exception error() {
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
                    downloadFile(session);
                    break;
            }

            session.disconnect();
        } catch (JSchException | IOException e) {
            success = false;
            error = e;
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
            command += fileLocation.substring(fileLocation.lastIndexOf('/')+1);
        }
        else{
            command += fileLocation;
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

        success = true;
    }

    private void downloadFile(final Session session) throws JSchException {
            Channel channel = session.openChannel("sftp");
            channel.connect();
            ChannelSftp sftp = (ChannelSftp) channel;

            copyFile(location, file, sftp);

            channel.disconnect();
            session.disconnect();
    }

    private void copyFile(String filename, File directory, ChannelSftp sftp) {
        try {
            Vector filenames = sftp.ls(filename);
            for (Object f : filenames) {
                ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) f;
                File file = new File(directory, entry.getFilename());
                InputStream is = sftp.get(entry.getFilename());
                Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                is.close();
            }
            success = true;
        } catch (SftpException | IOException e) {
            success = false;
            error = e;
        }
    }

}
