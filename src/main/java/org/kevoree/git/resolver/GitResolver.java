package org.kevoree.git.resolver;

import org.apache.maven.cli.MavenCli;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by duke on 8/24/14.
 */
public class GitResolver {

    private File basedir;

    public void init(File basedir) {
        this.basedir = basedir;
        if (!this.basedir.exists()) {
            this.basedir.mkdirs();
        }
    }

    public static void main(String[] args) {
        GitResolver resolver = new GitResolver();
        resolver.init(new File("sourceTree"));
        if (args.length == 1) {
            String url = args[0];
            File classes = resolver.getClasses(url);
            if (classes != null) {
                System.out.println(classes.getAbsolutePath());
                System.exit(0);
            }
            System.exit(-1);
        } else {
            System.err.println("Bad number of argument !");
            System.exit(-1);
        }
    }

    public File getClasses(String url) {
        Git git = get(url);
        if (git != null) {
            File targetClasses = new File(git.getRepository().getDirectory() + File.separator + "target" + File.separator + "classes");
            return targetClasses;
        }
        return null;
    }

    public Git get(String url) {
        MavenCli cli = new MavenCli();
        File target = createFromGitURL(url);
        try {
            org.eclipse.jgit.api.Git repo = Git.open(target);
            Iterable<RevCommit> logs = repo.log().call();
            RevCommit lastCommit = logs.iterator().next();
            ObjectId newID = repo.pull().call().getMergeResult().getNewHead();
            ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
            ByteArrayOutputStream baosErr = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(baosOut, true);
            PrintStream err = new PrintStream(baosErr, true);
            if (newID.compareTo(lastCommit) != 0) {
                System.out.println("Compiling " + url);
                int compilationResult = cli.doMain(new String[]{"clean", "compile"}, target.getAbsolutePath(), out, err);
                if (compilationResult != 0) {
                    System.err.println("Compilation error " + url + ", " + baosErr.toString());
                    return null;
                }
            }
            return repo;
        } catch (RepositoryNotFoundException e) {
        } catch (Exception ignore) {
            ignore.printStackTrace();
        }
        try {
            System.out.println("Cloning " + url);
            Git result = Git.cloneRepository().setDirectory(target).setURI(url).call();
            ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
            ByteArrayOutputStream baosErr = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(baosOut, true);
            PrintStream err = new PrintStream(baosErr, true);
            System.out.println("Compiling " + url);
            int compilationResult = cli.doMain(new String[]{"clean", "compile"}, target.getAbsolutePath(), out, err);
            if (compilationResult != 0) {
                System.err.println("Compilation error " + url + ", " + baosErr.toString());
                return null;
            }
            return result;
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static final String HTTPS_START = "https://";

    private static final String HTTP_START = "http://";

    private static final String GIT_START = "git@";

    private File createFromGitURL(String url) {
        String cleanURL = url;
        List<String> parts = new ArrayList<String>();
        if (cleanURL.startsWith(HTTPS_START)) {
            if (cleanURL.endsWith(".git")) {
                cleanURL = cleanURL.substring(0, cleanURL.length() - 4);
            }
            cleanURL = cleanURL.substring(HTTPS_START.length());
            String[] subParts = cleanURL.split("/");
            for (String sp : subParts) {
                if (!sp.equals("")) {
                    parts.add(sp);
                }
            }
        }
        if (cleanURL.startsWith(HTTP_START)) {
            cleanURL = cleanURL.substring(HTTP_START.length());
            if (cleanURL.endsWith(".git")) {
                cleanURL = cleanURL.substring(0, cleanURL.length() - 4);
            }
            String[] subParts = cleanURL.split("/");
            for (String sp : subParts) {
                if (!sp.equals("")) {
                    parts.add(sp);
                }
            }
        }
        if (cleanURL.startsWith(GIT_START)) {
            cleanURL = cleanURL.substring(GIT_START.length());
            if (cleanURL.endsWith(".git")) {
                cleanURL = cleanURL.substring(0, cleanURL.length() - 4);
            }
            parts.add(cleanURL.substring(0, cleanURL.indexOf(":")));
            cleanURL = cleanURL.substring(cleanURL.indexOf(":") + 1);
            String[] subParts = cleanURL.split("/");
            for (String sp : subParts) {
                if (!sp.equals("")) {
                    parts.add(sp);
                }
            }
        }
        StringBuilder pathBuilder = new StringBuilder();
        for (String part : parts) {
            pathBuilder.append(File.separator);
            pathBuilder.append(part);
        }
        File newdir = new File(basedir, pathBuilder.toString());
        if (!newdir.exists()) {
            newdir.mkdirs();
        }
        return newdir;
    }

}
