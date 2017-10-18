package org.primftpd.filesystem;

import org.primftpd.pojo.LsOutputBean;
import org.primftpd.pojo.LsOutputParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public abstract class RootFile<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final Shell.Interactive shell;

    protected final LsOutputBean bean;
    private final String absPath;
    protected final String name;

    public RootFile(Shell.Interactive shell, LsOutputBean bean, String absPath) {
        this.shell = shell;
        this.bean = bean;
        this.absPath = absPath;
        this.name = bean.getName();
    }

    protected abstract T createFile(Shell.Interactive shell, LsOutputBean bean, String absPath);

    public String getAbsolutePath() {
        logger.trace("[{}] getAbsolutePath() -> '{}'", name, absPath);
        return absPath;
    }

    public String getName() {
        logger.trace("[{}] getName()", name);
        return name;
    }

    public boolean isDirectory() {
        logger.trace("[{}] isDirectory() -> {}", name, bean.isDir());
        return bean.isDir();
    }

    public boolean isFile() {
        logger.trace("[{}] isFile() -> {}", name, bean.isFile());
        return bean.isFile();
    }

    public boolean doesExist() {
        logger.trace("[{}] doesExist() -> {}", name, bean.isExists());
        return bean.isExists();
    }

    public boolean isReadable() {
        logger.trace("[{}] isReadable()", name);
        return true;
    }

    public boolean isWritable() {
        logger.trace("[{}] isWritable()", name);
        return true;
    }

    public boolean isRemovable() {
        logger.trace("[{}] isRemovable()", name);
        return true;
    }

    public long getLastModified() {
        long time = bean.getDate().getTime();
        logger.trace("[{}] getLastModified() -> {}", name, time);
        return time;
    }

    public boolean setLastModified(long time) {
        logger.trace("[{}] setLastModified({})", name, time);
        // TODO root setLastModified()
        return false;
    }

    public long getSize() {
        logger.trace("[{}] getSize() -> {}", name, bean.getSize());
        return bean.getSize();
    }

    public boolean mkdir() {
        logger.trace("[{}] mkdir()", name);
        return runCommand("mkdir " + absPath);
    }

    public boolean delete() {
        logger.trace("[{}] delete()", name);
        return runCommand("rm -rf " + absPath);
    }

    public boolean move(RootFile<T> destination) {
        logger.trace("[{}] move({})", name, destination.getAbsolutePath());
        return runCommand("mv " + absPath + " " + destination.getAbsolutePath());
    }

    public List<T> listFiles() {
        logger.trace("[{}] listFiles()", name);

        List<T> result = new ArrayList<>();
        final LsOutputParser parser = new LsOutputParser();
        final List<LsOutputBean> beans = new ArrayList<>();
        shell.addCommand("ls -lA " + absPath, 0, new Shell.OnCommandLineListener() {
            @Override
            public void onLine(String s) {
                LsOutputBean bean = parser.parseLine(s);
                if (bean != null) {
                    beans.add(bean);
                }
            }
            @Override
            public void onCommandResult(int i, int i1) {
            }
        });
        shell.waitForIdle();

        for (LsOutputBean bean : beans) {
            String path = absPath + "/" + bean.getName();
            result.add(createFile(shell, bean, path));
        }

        return result;
    }

    public OutputStream createOutputStream(long offset) throws IOException {
        logger.trace("[{}] createOutputStream(offset: {})", name, offset);

        // new file or existing file?
        final String pathToUpdatePerm;
        if (bean.isExists()) {
            pathToUpdatePerm = absPath;
        } else {
            pathToUpdatePerm = absPath.substring(0, absPath.lastIndexOf('/'));
        }

        // remember current permission
        final String perm = readCommandOutput("stat -c %a " + pathToUpdatePerm);

        // set perm to be able to read file
        runCommand("chmod 0777 " + pathToUpdatePerm);

        return new FileOutputStream(absPath) {
            @Override
            public void close() throws IOException {
                super.close();
                // restore permission
                runCommand("chmod 0" + perm + " " + pathToUpdatePerm);
            }
        };
    }

    public InputStream createInputStream(long offset) throws IOException {
        logger.trace("[{}] createInputStream(offset: {})", name, offset);

        // remember current permission
        final String perm = readCommandOutput("stat -c %a " + absPath);

        // set perm to be able to read file
        runCommand("chmod 0777 " + absPath);

        return new FileInputStream(absPath) {
            @Override
            public void close() throws IOException {
                super.close();
                // restore permission
                runCommand("chmod 0" + perm + " " + absPath);
            }
        };
    }

    protected boolean runCommand(String cmd) {
        logger.trace("running cmd: '{}'", cmd);
        final Boolean[] wrapper = new Boolean[1];
        shell.addCommand(cmd, 0, new Shell.OnCommandLineListener() {
            @Override
            public void onLine(String s) {
            }
            @Override
            public void onCommandResult(int i, int i1) {
                wrapper[0] = i == 0;
            }
        });
        shell.waitForIdle();
        return wrapper[0];
    }

    protected String readCommandOutput(String cmd) {
        final StringBuilder sb = new StringBuilder();
        shell.addCommand(cmd, 0, new Shell.OnCommandLineListener() {
            @Override
            public void onCommandResult(int i, int i1) {
            }
            @Override
            public void onLine(String s) {
                if (s != null) {
                    sb.append(s);
                }
            }
        });
        shell.waitForIdle();
        String result = sb.toString();
        logger.trace("read output of cmd '{}': '{}'", cmd, result);
        return result;
    }
}