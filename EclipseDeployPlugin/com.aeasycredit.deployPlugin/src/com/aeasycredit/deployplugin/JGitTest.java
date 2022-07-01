package com.aeasycredit.deployplugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

// http://www.codeaffine.com/2015/11/30/jgit-clone-repository/
// http://www.codeaffine.com/2015/12/15/getting-started-with-jgit/
public class JGitTest {
    public static void main(String[] args) throws Exception {
        String username = "";
        String password = "";
        
//        FileRepositoryBuilder builder = new FileRepositoryBuilder();
//        Repository repository = builder.setGitDir(new File("/Developer/workspace/gittest"))
//          .readEnvironment() // scan environment GIT_* variables
//          .findGitDir() // scan up the file system tree
//          .build();
//        
//        if (!repository.getObjectDatabase().exists()) {
//            Git.cloneRepository()
//                    .setURI("http://gitlab.aeasycredit.net/dave.zhao/my-test")
//                    .setDirectory(new File("/Developer/workspace/gittest"))
//                    .setCloneAllBranches(true)
//                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
//                    .call();
//        }
        
        // https://github.com/eclipse/jgit/tree/master/org.eclipse.jgit.test/tst/org/eclipse/jgit/api
        // http://qinghua.github.io/jgit/
        
        // Get the url of the remote git.
        Git git1 = Git.open(new File("/Developer/workspace/config-server"));
        String repoPath = git1.getRepository().getConfig().getString( "remote", "origin", "url" );
        System.out.println("repoPath--->" + repoPath);
        
        // Clone repository if the local repository isn't exist.
        String gitFolder = "/tmp/config-server";
        if(!new File(gitFolder).exists()) {
	        Git.cloneRepository()
	            .setURI(repoPath)
	            .setDirectory(new File(gitFolder))
	            .setCloneAllBranches(true)
	            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
	            .call();
        }
        
        // checkout develop branch
        Git git = Git.open(new File(gitFolder));
//        git.checkout().setName("origin/1.19.x").call();
        String branchName = "1.19.x";
        
        Ref ref = git.getRepository().exactRef("refs/heads/" + branchName);
        // Check the local repo exist?
        if(ref == null) {
        	// didn't exist, checkout
            git.checkout().
                    setCreateBranch(true).
                    setName(branchName).
                    setStartPoint("origin/" + branchName).
                    call();
        }
        
        String newBranchName = "1.19.0.release";
        Ref newRef = git.getRepository().exactRef("refs/heads/" + newBranchName);
        if(newRef == null) {
        	// check the release branch exist? if didn't exist, create first.
        	git.checkout().setCreateBranch(true).setName(newBranchName).call();
        } else {
        	// if local branch exist, just checkout
        	git.checkout().setName(newBranchName).call();
        	// pull repo from remote.
        	try {
            	git.pull()
    			.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
    			.call();
        	} catch(Exception e) {
        		// don't throw exception 
//        		e.printStackTrace();
        	}
        }
        
        // push the release branch to remote repo.
		git.push().setRemote("origin")
		.setRefSpecs(new RefSpec("refs/heads/"+newBranchName+":refs/heads/"+newBranchName))
		.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
		.call();
        
        // Tag the release branch for prod
    	SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmm");
    	String dateString = formatter.format(new Date());
    	String tagName = newBranchName+"-"+dateString;
    	Ref tagRef = git.getRepository().exactRef("refs/tags/" + tagName);
    	// local tag exist?
    	if(tagRef == null) {
    		// if didn't exist, created.
        	git.tag().setName(tagName).setMessage("For prod...").call();
    	}
        
        // push the tag to remote repo.
		git.push().setRemote("origin")
		.setRefSpecs(new RefSpec("refs/tags/"+tagName+":refs/tags/"+tagName))
		.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
		.call();
        
        
//        List<Ref> branchList = git.branchList().call();
//        System.out.println("branchList="+branchList);
//        
//        
//        
//        git.checkout().setCreateBranch(true).setName("2.19.0.release").call();
//        
//        git.push()
//			.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
//			.call();
//        
//        Collection<Ref> refs = git.lsRemote()
//        		.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
//        		.call();
//        for (Ref ref : refs) {
//            System.out.println("ref--->" + ref.getName());
//            
//        }
        git.close();
    }
}
