    package io.seqware;

import java.util.Map;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sourceforge.seqware.pipeline.workflowV2.AbstractWorkflowDataModel;
import net.sourceforge.seqware.pipeline.workflowV2.model.Job;
import net.sourceforge.seqware.pipeline.workflowV2.model.SqwFile;
import java.util.Date;
import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class DELLYWorkflow extends AbstractWorkflowDataModel {

    private boolean breakpoint=false;
    private String delly_bin;
    private String cov_bin;
    private String cov_plot;
    private String vcfcombine_bin;
    private String vcf_sort_bin;
    private String gcnorm_r;
    private String rscript_bin;

    private String prepare_uploader_bin;
    private String uploader_bin;
    private String cleanup_bin;

    private String somatic_filter;
    private String delly2bed;
    private String delly_pe_dump;

    private String ref_genome_path = "";
    private String ref_genome_gc_path = "";
    
    private String resultsDirRoot = "results/";
    private String resultsDirDelly = "results/delly";
    private String resultsDirJumpy = "results/jumpy";
    private String resultsDirDuppy = "results/duppy";
    private String resultsDirInvy = "results/invy";
    private String resultsDirCov = "results/cov";
    private String resultsDirCovPlot = "results/cov/plot";

    String workflowID = null;
    String inputBamPathTumor = null;
    String inputBamPathGerm = null;  

    // used to download with gtdownload
    String gnosInputFileURLTumor = null;
    String gnosInputFileURLGerm = null;
    String gnosInputMetaDataURLTumor = null;
    String gnosInputMetaDataURLGerm = null;
    String gnosUploadFileURL = null;
    String gnosUploadFileDir = null;
    String gnosKey = null;


    private void init() {
	try {
      
      if (hasPropertyAndNotNull("breakpoint")) {
          breakpoint = Boolean.valueOf(getProperty("breakpoint"));
      }
      inputBamPathTumor = getProperty("input_bam_path_tumor");

      gnosInputFileURLTumor = getProperty("gnos_input_file_url_tumor");

      gnosInputMetaDataURLTumor = getProperty("gnos_input_metadata_url_tumor");

      inputBamPathGerm = getProperty("input_bam_path_germ");

      gnosInputFileURLGerm = getProperty("gnos_input_file_url_germ");

      gnosInputMetaDataURLGerm = getProperty("gnos_input_metadata_url_germ");

      gnosUploadFileURL = getProperty("gnos_output_file_url");
      gnosUploadFileDir = getProperty("gnos_output_file_dir");
      gnosKey = getProperty("gnos_key");

      workflowID = getProperty("delly_workflowID");

      delly_bin = getProperty("delly_bin");
      cov_bin = getProperty("cov_bin");
      cov_plot = getProperty("cov_plot");
      gcnorm_r = getProperty("gcnorm_r");
      vcfcombine_bin = getProperty("vcfcombine_bin");
      vcf_sort_bin = getProperty("vcf_sort_bin");
      rscript_bin = getProperty("rscript_bin");
      prepare_uploader_bin = getProperty("prepare_uploader_bin");
      uploader_bin = getProperty("uploader_bin");

      cleanup_bin = getProperty("cleanup_bin");

      somatic_filter = getProperty("somatic_filter");
      delly2bed = getProperty("delly2bed");
      delly_pe_dump = getProperty("delly_pe_dump");

      ref_genome_path = getProperty("ref_genome_path");
      ref_genome_gc_path = getProperty("ref_genome_gc_path");

	} catch (Exception e) {
	    e.printStackTrace();
	    throw new RuntimeException(e);
	}
    }

    @Override
    public void setupDirectory() {
        //since setupDirectory is the first method run, we use it to initialize variables too.
        init();
       

        this.addDirectory(resultsDirDelly);
        this.addDirectory(resultsDirDuppy);
        this.addDirectory(resultsDirInvy);
        this.addDirectory(resultsDirJumpy);
        this.addDirectory(resultsDirCov);
        this.addDirectory(resultsDirCovPlot);

    }
 
    @Override
    public Map<String, SqwFile> setupFiles() {
      try {        
          if (breakpoint == true) {
              SqwFile ref_genome = this.createFile("ref_gen");
              ref_genome.setSourcePath(ref_genome_path);
              ref_genome.setIsInput(true);  
          }
          SqwFile ref_genome_gc = this.createFile("ref_gen_gc");
          ref_genome_gc.setSourcePath(ref_genome_gc_path);
          ref_genome_gc.setIsInput(true);  

      } catch (Exception ex) {
        ex.printStackTrace();
        throw new RuntimeException(ex);
      }
      return this.getFiles();
    }
    
   
    @Override
    public void buildWorkflow() {
        
        String ref_gen_path = " ";
        String ref_gen_gc_path = " ";
        ArrayList<Job> downloadJobs = new ArrayList<Job>();

        // DOWNLOAD DATA

        Job gtDownloadJob1 = this.getWorkflow().createBashJob("gtdownload1");
        gtDownloadJob1.getCommand().addArgument("gtdownload")
            .addArgument("-c " + gnosKey)
            .addArgument("-v -d")
            .addArgument(gnosInputFileURLTumor);
        downloadJobs.add(gtDownloadJob1);
        Job gtDownloadJob2 = this.getWorkflow().createBashJob("gtdownload2");
        gtDownloadJob2.getCommand().addArgument("gtdownload")
            .addArgument("-c " + gnosKey)
            .addArgument("-v -d")
            .addArgument(gnosInputFileURLGerm);
        downloadJobs.add(gtDownloadJob2);

        //convert provisioned files from soft to hard links
        Job linkConvert = this.getWorkflow().createBashJob("linkConvert");
        linkConvert.getCommand().addArgument("find provisionfiles -type l -exec bash -c 'ln -f \"$(readlink -m \"$0\")\" \"$0\"' {} \\;");

        
        //prepare output
        
        String tumorFile = inputBamPathTumor;
        String germFile = inputBamPathGerm;
             
        String[] tumorName = tumorFile.split("/");
        String[] germName = germFile.split("/");
        //String samplePair =tumorName[0] + "_vs_" + germName[0];
        String samplePair =tumorName[0];
        if (breakpoint == true) {
        //    samplePair += ".bp";
            ref_gen_path = this.getFiles().get("ref_gen").getProvisionedPath();
        }
        ref_gen_gc_path = this.getFiles().get("ref_gen_gc").getProvisionedPath();

        String logFileDelly=resultsDirDelly + "/" + samplePair + ".deletions.log";
        String outputFileDelly=resultsDirDelly + "/" + samplePair + ".deletions";
        String outputFileDellyFilter=resultsDirDelly + "/" + samplePair + ".deletions.somatic";
        String outputFileDellyFilterConf=resultsDirDelly + "/" + samplePair + ".deletions.somatic.highConf";
        String outputFileDellyFilterGerm=resultsDirDelly + "/" + samplePair + ".deletions.germline";
        String outputFileDellyFilterConfGerm=resultsDirDelly + "/" + samplePair + ".deletions.germline.highConf";
        String outputFileDellyDump=resultsDirDelly + "/" + samplePair + ".deletions.pe_dump.txt";
        String outputFileDellySomaticDump=resultsDirDelly + "/" + samplePair + ".deletions.somatic.readname.txt";
        String outputFileDellyGermDump=resultsDirDelly + "/" + samplePair + ".deletions.germline.readname.txt";

        String logFileDuppy=resultsDirDuppy + "/" + samplePair + ".duplications.log";
        String outputFileDuppy=resultsDirDuppy + "/" + samplePair + ".duplications";
        String outputFileDuppyFilter=resultsDirDuppy + "/" + samplePair + ".duplications.somatic";
        String outputFileDuppyFilterConf=resultsDirDuppy + "/" + samplePair + ".duplications.somatic.highConf";
        String outputFileDuppyFilterGerm=resultsDirDuppy + "/" + samplePair + ".duplications.germline";
        String outputFileDuppyFilterConfGerm=resultsDirDuppy + "/" + samplePair + ".duplications.germline.highConf";
        String outputFileDuppyDump=resultsDirDuppy + "/" + samplePair + ".duplications.pe_dump.txt";
        String outputFileDuppySomaticDump=resultsDirDuppy + "/" + samplePair + ".duplications.somatic.readname.txt";
        String outputFileDuppyGermDump=resultsDirDuppy + "/" + samplePair + ".duplications.germline.readname.txt";

        String logFileInvy=resultsDirInvy + "/" + samplePair + ".inversions.log";
        String outputFileInvy=resultsDirInvy + "/" + samplePair + ".inversions";
        String outputFileInvyFilter=resultsDirInvy + "/" + samplePair + ".inversions.somatic";
        String outputFileInvyFilterConf=resultsDirInvy + "/" + samplePair + ".inversions.somatic.highConf";
        String outputFileInvyFilterGerm=resultsDirInvy + "/" + samplePair + ".inversions.germline";
        String outputFileInvyFilterConfGerm=resultsDirInvy + "/" + samplePair + ".inversions.germline.highConf";
        String outputFileInvyDump=resultsDirInvy + "/" + samplePair + ".inversions.pe_dump.txt";
        String outputFileInvySomaticDump=resultsDirInvy + "/" + samplePair + ".inversions.somatic.readname.txt";
        String outputFileInvyGermDump=resultsDirInvy + "/" + samplePair + ".inversions.germline.readname.txt";

        String logFileJumpy=resultsDirJumpy + "/" + samplePair + ".translocations.log";
        String outputFileJumpy=resultsDirJumpy + "/" + samplePair + ".translocations";
        String outputFileJumpyFilter=resultsDirJumpy + "/" + samplePair + ".translocations.somatic";
        String outputFileJumpyFilterConf=resultsDirJumpy + "/" + samplePair + ".translocations.somatic.highConf";
        String outputFileJumpyFilterGerm=resultsDirJumpy + "/" + samplePair + ".translocations.germline";
        String outputFileJumpyFilterConfGerm=resultsDirJumpy + "/" + samplePair + ".translocations.germline.highConf";
        String outputFileJumpyDump=resultsDirJumpy + "/" + samplePair + ".translocations.pe_dump.txt";
        String outputFileJumpySomaticDump=resultsDirJumpy + "/" + samplePair + ".translocations.somatic.readname.txt";
        String outputFileJumpyGermDump=resultsDirJumpy + "/" + samplePair + ".translocations.germline.readname.txt";

        String outputFileCovGerm1=resultsDirCov + "/" + germName[0] + "_1kb.cov";
        String outputFileCovGerm1Log=resultsDirCov + "/" + germName[0] + "_1kb.log";
        String outputFileCovGerm2=resultsDirCov + "/" + germName[0] + "_10kb.cov";
        String outputFileCovGerm2Log=resultsDirCov + "/" + germName[0] + "_10kb.log";

        String outputFileCovGermGcnorm=resultsDirCov + "/" + germName[0] + ".gcnorm.cov";

        String outputFileCovTumor1=resultsDirCov + "/" + tumorName[0] + "_1kb.cov";
        String outputFileCovTumor1Log=resultsDirCov + "/" + tumorName[0] + "_1kb.log";
        String outputFileCovTumor2=resultsDirCov + "/" + tumorName[0] + "_10kb.cov";
        String outputFileCovTumor2Log=resultsDirCov + "/" + tumorName[0] + "_10kb.log";

        String outputFileCovTumorGcnorm=resultsDirCov + "/" + tumorName[0] + ".gcnorm.cov";
              
    
        //7 jobs per downloaded BAM pair (DELLY,DUPPY,INVY,JUMPY, 3xCOV)
        
        Job dellyJob = this.getWorkflow().createBashJob("delly_job");
        dellyJob.getCommand().addArgument(delly_bin)
            .addArgument("-t DEL")
            .addArgument("-s 9")
            .addArgument(breakpoint == true ? "-g " + ref_gen_path : " ")
            .addArgument("-q 1")
            .addArgument("-p " + outputFileDellyDump)
            .addArgument("-o " + outputFileDelly + ".vcf")
            .addArgument(tumorFile)
            .addArgument(germFile)
            .addArgument(" &> " + logFileDelly);
        dellyJob.addParent(downloadJobs.get(0));
        dellyJob.addParent(downloadJobs.get(1));
        
        // Job dellyFilterJob1 = this.getWorkflow().createBashJob("delly_filter_job1");
        // dellyFilterJob1.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileDelly + ".vcf")
        //     .addArgument("-o " + outputFileDelly + ".bedpe.txt");
        // dellyFilterJob1.addParent(dellyJob);
        
        // Job dellyFilterJob2 = this.getWorkflow().createBashJob("delly_filter_job2");
        // dellyFilterJob2.getCommand().addArgument(somatic_filter)
        //     .addArgument("-v " + outputFileDelly + ".vcf")
        //     .addArgument("-o " + outputFileDellyFilter + ".vcf");
        // dellyFilterJob2.addParent(dellyJob);

        // Job dellyFilterJob3 = this.getWorkflow().createBashJob("delly_filter_job3");
        // dellyFilterJob3.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileDellyFilter + ".vcf")
        //     .addArgument("-o " + outputFileDellyFilter + ".bedpe.txt");
        // dellyFilterJob3.addParent(dellyFilterJob2);

        // Job dellyFilterJob4 = this.getWorkflow().createBashJob("delly_filter_job4");
        // dellyFilterJob4.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileDellyFilterConf + ".vcf")
        //     .addArgument("-o " + outputFileDellyFilterConf + ".bedpe.txt");
        // dellyFilterJob4.addParent(dellyFilterJob3);



        //DUPPY
        Job duppyJob = this.getWorkflow().createBashJob("duppy_job");
        duppyJob.getCommand().addArgument(delly_bin)
            .addArgument("-t DUP")
            //.addArgument("-s 9")
            .addArgument(breakpoint == true ? "-g " + ref_gen_path : " ")
            .addArgument("-q 1")
            .addArgument("-p " + outputFileDuppyDump)
            .addArgument("-o " + outputFileDuppy + ".vcf")
            .addArgument(tumorFile)
            .addArgument(germFile)
            .addArgument(" &> " + logFileDuppy);
        duppyJob.addParent(downloadJobs.get(0));
        duppyJob.addParent(downloadJobs.get(1));

        // Job duppyFilterJob1 = this.getWorkflow().createBashJob("duppy_filter_job1");
        // duppyFilterJob1.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileDuppy + ".vcf")
        //     .addArgument("-o " + outputFileDuppy + ".bedpe.txt");
        // duppyFilterJob1.addParent(duppyJob);
        
        // Job duppyFilterJob2 = this.getWorkflow().createBashJob("duppy_filter_job2");
        // duppyFilterJob2.getCommand().addArgument(somatic_filter)
        //     .addArgument("-v " + outputFileDuppy + ".vcf")
        //     .addArgument("-o " + outputFileDuppyFilter + ".vcf");
        // duppyFilterJob2.addParent(duppyJob);

        // Job duppyFilterJob3 = this.getWorkflow().createBashJob("duppy_filter_job3");
        // duppyFilterJob3.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileDuppyFilter + ".vcf")
        //     .addArgument("-o " + outputFileDuppyFilter + ".bedpe.txt");
        // duppyFilterJob3.addParent(duppyFilterJob2);

        // Job duppyFilterJob4 = this.getWorkflow().createBashJob("duppy_filter_job4");
        // duppyFilterJob4.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileDuppyFilterConf + ".vcf")
        //     .addArgument("-o " + outputFileDuppyFilterConf + ".bedpe.txt");
        // duppyFilterJob4.addParent(duppyFilterJob3);        


        //INVY
        Job invyJob = this.getWorkflow().createBashJob("invy_job");
        invyJob.getCommand().addArgument(delly_bin)
            .addArgument("-t INV")
            .addArgument("-q 1")
            .addArgument(breakpoint == true ? "-g " + ref_gen_path : " ")
            .addArgument("-p " + outputFileInvyDump)
            .addArgument("-o " + outputFileInvy + ".vcf")
            .addArgument(tumorFile)
            .addArgument(germFile)
            .addArgument(" &> " + logFileInvy);
        invyJob.addParent(downloadJobs.get(0));
        invyJob.addParent(downloadJobs.get(1));

        //  Job invyFilterJob1 = this.getWorkflow().createBashJob("invy_filter_job1");
        // invyFilterJob1.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileInvy + ".vcf")
        //     .addArgument("-o " + outputFileInvy + ".bedpe.txt");
        // invyFilterJob1.addParent(invyJob);
        
        // Job invyFilterJob2 = this.getWorkflow().createBashJob("invy_filter_job2");
        // invyFilterJob2.getCommand().addArgument(somatic_filter)
        //     .addArgument("-v " + outputFileInvy + ".vcf")
        //     .addArgument("-o " + outputFileInvyFilter + ".vcf");
        // invyFilterJob2.addParent(invyJob);

        // Job invyFilterJob3 = this.getWorkflow().createBashJob("invy_filter_job3");
        // invyFilterJob3.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileInvyFilter + ".vcf")
        //     .addArgument("-o " + outputFileInvyFilter + ".bedpe.txt");
        // invyFilterJob3.addParent(invyFilterJob2);

        // Job invyFilterJob4 = this.getWorkflow().createBashJob("invy_filter_job4");
        // invyFilterJob4.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileInvyFilterConf + ".vcf")
        //     .addArgument("-o " + outputFileInvyFilterConf + ".bedpe.txt");
        // invyFilterJob4.addParent(invyFilterJob3);        

        
        //JUMPY
        Job jumpyJob = this.getWorkflow().createBashJob("jumpy_job");
        jumpyJob.getCommand().addArgument(delly_bin)
            .addArgument("-t TRA")
            .addArgument("-q 1")
            //.addArgument(breakpoint == true ? "-g " + ref_gen_path : " ") Not run breakpoint for jumpy
            .addArgument("-p " + outputFileJumpyDump)
            .addArgument("-o " + outputFileJumpy + ".vcf")
            .addArgument(tumorFile)
            .addArgument(germFile)
            .addArgument(" &> " + logFileJumpy);
        jumpyJob.addParent(downloadJobs.get(0));
        jumpyJob.addParent(downloadJobs.get(1));

        //  Job jumpyFilterJob1 = this.getWorkflow().createBashJob("jumpy_filter_job1");
        // jumpyFilterJob1.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileJumpy + ".vcf")
        //     .addArgument("-o " + outputFileJumpy + ".bedpe.txt");
        // jumpyFilterJob1.addParent(jumpyJob);
        
        // Job jumpyFilterJob2 = this.getWorkflow().createBashJob("jumpy_filter_job2");
        // jumpyFilterJob2.getCommand().addArgument(somatic_filter)
        //     .addArgument("-v " + outputFileJumpy + ".vcf")
        //     .addArgument("-o " + outputFileJumpyFilter + ".vcf");
        // jumpyFilterJob2.addParent(jumpyJob);

        // Job jumpyFilterJob3 = this.getWorkflow().createBashJob("jumpy_filter_job3");
        // jumpyFilterJob3.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileJumpyFilter + ".vcf")
        //     .addArgument("-o " + outputFileJumpyFilter + ".bedpe.txt");
        // jumpyFilterJob3.addParent(jumpyFilterJob2);

        // Job jumpyFilterJob4 = this.getWorkflow().createBashJob("jumpy_filter_job4");
        // jumpyFilterJob4.getCommand().addArgument(delly2bed)
        //     .addArgument("-v " + outputFileJumpyFilterConf + ".vcf")
        //     .addArgument("-o " + outputFileJumpyFilterConf + ".bedpe.txt");
        // jumpyFilterJob4.addParent(jumpyFilterJob3);        


        //COV + plot jobs
        Job covJobGerm1 = this.getWorkflow().createBashJob("cov_job_germ1");
        covJobGerm1.getCommand().addArgument(cov_bin)
            .addArgument("-s 1000")
            .addArgument("-o 1000")
            .addArgument(germFile)
            .addArgument("-f " + outputFileCovGerm1)
            .addArgument(" &> " + outputFileCovGerm1Log);
        covJobGerm1.addParent(downloadJobs.get(0));
        covJobGerm1.addParent(downloadJobs.get(1));

        Job covJobGerm2 = this.getWorkflow().createBashJob("cov_job_germ2");
        covJobGerm2.getCommand().addArgument(cov_bin)
            .addArgument("-s 10000")
            .addArgument("-o 10000")
            .addArgument(germFile)
            .addArgument("-f " + outputFileCovGerm2)
            .addArgument(" &> " + outputFileCovGerm2Log);
        covJobGerm2.addParent(downloadJobs.get(0));
        covJobGerm2.addParent(downloadJobs.get(1));

        Job covJobGerm3 = this.getWorkflow().createBashJob("cov_job_germ3");
        covJobGerm3.getCommand().addArgument(rscript_bin  + " " + gcnorm_r)
            .addArgument(outputFileCovGerm2)
            .addArgument(ref_gen_gc_path)
            .addArgument(outputFileCovGermGcnorm);
        covJobGerm3.addParent(covJobGerm2);

        Job covJobTumor1 = this.getWorkflow().createBashJob("cov_job_tumor1");
        covJobTumor1.getCommand().addArgument(cov_bin)
            .addArgument("-s 1000")
            .addArgument("-o 1000")
            .addArgument(tumorFile)
            .addArgument("-f " + outputFileCovTumor1)
            .addArgument(" &> " + outputFileCovTumor1Log);
        covJobTumor1.addParent(downloadJobs.get(0));
        covJobTumor1.addParent(downloadJobs.get(1));

        Job covJobTumor2 = this.getWorkflow().createBashJob("cov_job_tumor2");
        covJobTumor2.getCommand().addArgument(cov_bin)
            .addArgument("-s 10000")
            .addArgument("-o 10000")
            .addArgument(tumorFile)
            .addArgument("-f " + outputFileCovTumor2)
            .addArgument(" &> " + outputFileCovTumor2Log);
        covJobTumor2.addParent(downloadJobs.get(0));
        covJobTumor2.addParent(downloadJobs.get(1));

        Job covJobTumor3 = this.getWorkflow().createBashJob("cov_job_tumor3");
        covJobTumor3.getCommand().addArgument(rscript_bin  + " " + gcnorm_r)
            .addArgument(outputFileCovTumor2)
            .addArgument(ref_gen_gc_path)
            .addArgument(outputFileCovTumorGcnorm);
        covJobTumor3.addParent(covJobTumor2);
             
    
        Job covJobPlot = this.getWorkflow().createBashJob("cov_job_plot");
        covJobPlot.getCommand().addArgument(cov_plot  + " " + outputFileCovGermGcnorm)
            .addArgument(outputFileCovTumorGcnorm)
            .addArgument(resultsDirCovPlot);
        covJobPlot.addParent(covJobGerm3);
        covJobPlot.addParent(covJobTumor3);


        //check and upload results
        String currdateStamp = new SimpleDateFormat("yyyyMMdd").format(Calendar.getInstance().getTime());
        String delly_somatic = samplePair + "." + workflowID + "." + currdateStamp + ".somatic.vcf.gz";
        String delly_bedpe_somatic = samplePair + "." + workflowID + "." + currdateStamp + ".somatic.bedpe.txt";
        String cov_somatic = samplePair + "." + workflowID + "." + currdateStamp + ".cov";
        String delly_germline = samplePair + "." + workflowID + "." + currdateStamp + ".germline.vcf.gz";
        String delly_bedpe_germline = samplePair + "." + workflowID + "." + currdateStamp + ".germline.bedpe.txt";
        String delly_log = resultsDirRoot + samplePair + "." + workflowID + "." + currdateStamp + ".log";
        String delly_somatic_pe_dump = resultsDirRoot  + samplePair + "." + workflowID + "." + currdateStamp + ".somatic.readname.txt";
        String delly_germline_pe_dump = resultsDirRoot  + samplePair + "." + workflowID + "." + currdateStamp + ".germline.readname.txt";

        Job prepareUploadJobSomatic = this.getWorkflow().createBashJob("prepare_upload_job_somatic");
        prepareUploadJobSomatic.getCommand().addArgument(prepare_uploader_bin + " " + delly2bed  + " " + resultsDirRoot + " " + delly_somatic + " " + outputFileDellyFilterConf + ".vcf" + " " + outputFileDuppyFilterConf + ".vcf" + " " + outputFileInvyFilterConf + ".vcf" + " " + outputFileJumpyFilterConf + ".vcf" + " " + cov_somatic + " " + resultsDirCov);
        prepareUploadJobSomatic.addParent(covJobPlot);

        Job prepareUploadJobGermline = this.getWorkflow().createBashJob("prepare_upload_job_germline");
        prepareUploadJobGermline.getCommand().addArgument(prepare_uploader_bin  + " " + delly2bed + " " + resultsDirRoot + " " + delly_germline + " " + outputFileDellyFilterConfGerm + ".vcf" + " " + outputFileDuppyFilterConfGerm + ".vcf" + " " + outputFileInvyFilterConfGerm + ".vcf" + " " + outputFileJumpyFilterConfGerm + ".vcf");
        prepareUploadJobGermline.addParent(prepareUploadJobSomatic);

        Job uploadJob = this.getWorkflow().createBashJob("upload_job");
        uploadJob.getCommand().addArgument("/usr/bin/perl " + uploader_bin)
            .addArgument("--metadata-urls " + samplePair)
            .addArgument("--vcfs " + resultsDirRoot + "/" + delly_somatic + ", " + resultsDirRoot + "/" + delly_germline)
            .addArgument("--vcf-md5sum-files " + resultsDirRoot + "/" + delly_somatic + ".md5" + ", " + resultsDirRoot + "/" + delly_germline + ".md5")
            .addArgument("--vcf-idxs " + resultsDirRoot + "/" + delly_somatic + ".tbi" + ", " + resultsDirRoot + "/" + delly_germline + ".tbi")
            .addArgument("--vcf-idx-md5sum-files " + resultsDirRoot + "/" + delly_somatic + ".tbi.md5" + ", " + resultsDirRoot + "/" + delly_germline + ".tbi.md5")
            .addArgument("--tarballs " + resultsDirRoot + "/" + delly_bedpe_somatic  + ".tar.gz" + " " + resultsDirRoot + "/" + delly_bedpe_germline  + ".tar.gz" + " "  + cov_somatic + ".tar.gz")
            .addArgument("--tarball-md5sum-files " + resultsDirRoot + "/" + delly_bedpe_somatic  + ".tar.gz.md5" + " " + resultsDirRoot + "/" + delly_bedpe_germline  + ".tar.gz.md5" + " "  + cov_somatic + ".tar.gz.md5")
            .addArgument("--outdir " + gnosUploadFileDir)
            .addArgument("--key " + gnosKey)
            .addArgument("--upload-url " + gnosUploadFileURL)
            .addArgument("--study-refname-override " + "icgc_pancancer_vcf_test")
           // .addArgument([--workflow-src-url <http://... the source repo>])
           // .addArgument([--workflow-url <http://... the packaged SeqWare Zip>])
           // .addArgument([--workflow-name <workflow_name>])
           // .addArgument([--workflow-version <workflow_version>])
           // .addArgument([--seqware-version <seqware_version_workflow_compiled_with>])
           // .addArgument([--description-file <file_path_for_description_txt>])
           // .addArgument([--study-refname-override <study_refname_override>])
           // .addArgument([--analysis-center-override <analysis_center_override>])
           // .addArgument([--pipeline-json <pipeline_json_file>])
           // .addArgument([--qc-metrics-json <qc_metrics_json_file>])
           // .addArgument([--timing-metrics-json <timing_metrics_json_file>])
           // .addArgument([--make-runxml])
           // .addArgument([--make-expxml])
           // .addArgument([--force-copy])
           // .addArgument([--skip-validate])
           // .addArgument([--skip-upload])
           .addArgument("--test");
        uploadJob.addParent(prepareUploadJobGermline);

        //TODO
        //cleanup data downloaded + created
        //add README file:
        // include a README in your tar.gz file that explains what each file is for
        // include a directory inside the tar.gz file so, when the file is extracted, it produces a directory named the same as the root of the tar.gz file e.g. <dir_name>.tar.gz.

        Job cleanupJob = this.getWorkflow().createBashJob("cleanup_job");
        cleanupJob.getCommand().addArgument(cleanup_bin  + " " + resultsDirRoot + " " + inputBamPathGerm + " " + inputBamPathTumor);
        cleanupJob.addParent(uploadJob);

        
        
    }
}
