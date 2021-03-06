import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
 
public class BizAgent implements Daemon, Runnable {
    private String status = "";
    private int no = 0;
    private Thread thread = null;
    public Logger log = Logger.getLogger(getClass());
    
    Properties p = new Properties();
    static Properties init_p = new Properties();
    
    private final String DB_URL = "jdbc:mysql://210.114.225.53/dhn?characterEncoding=utf8";  
    //private final String DB_URL = "jdbc:mysql://222.122.203.68/dhn?characterEncoding=utf8";
    private boolean isStop = false;
    public static int GRS_Proc_cnt = 0;
    BizDBCPInit bizDBCP;
    SmtDBCPInit smtDBCP;

    @Override
    public void init(DaemonContext context) throws DaemonInitException, Exception {
        System.out.println("init...");
        String[] args = context.getArguments();
        if(args != null) {
            for(String arg : args) {
                System.out.println(arg);
            }
        }
        
        try {
        	//p.load(new FileInputStream("E:\\Git\\BizAgent\\conf\\log4j.properties")); 
        	//p.load(new FileInputStream("D:\\BIZ\\BizAgent\\BizAgent\\conf\\log4j.properties")); 
        	p.load(new FileInputStream("/root/BizAgent/conf/log4j.properties"));
        	PropertyConfigurator.configure(p);
        	log.info("Log Property Load !!");
            status = "INITED";
            
            this.thread = new Thread(this);
            
            bizDBCP = BizDBCPInit.getInstance(log);
            
            log.info("init OK.");
            
            //init_p.load(new FileInputStream("E:\\Git\\BizAgent\\conf\\init.properties")); 
        	//init_p.load(new FileInputStream("D:\\BIZ\\BizAgent\\BizAgent\\conf\\init.properties")); 
        	init_p.load(new FileInputStream("/root/BizAgent/conf/init.properties"));;

            if(init_p.get("SMTPHNDB").equals("1"))
            	smtDBCP = SmtDBCPInit.getInstance(log);
            
            log.info("Init Properties Load OK!!");
            
        } catch(IOException e) {
        	log.info("../conf/log4j.properties 파일 없어");
        }

    }
 
    @Override
    public void start() {
        status = "STARTED";
        this.thread.start();
        log.info("Biz Agent start OK. ");
        isStop = false;
    }
 
    @Override
    public void stop() throws Exception {
        status = "STOPED";
        //this.thread.join(10);
        isStop = true;
        log.info("Biz Agent stop OK.");
    }
 
    @Override
    public void destroy() {
        status = "DESTROIED";
        log.info("Biz Agent destory OK.");
    }
    
    public static Properties getProp() {
    	return init_p;
    }
 
    @Override
    public void run() {
    	
    	String PreMonth = "";
    	boolean isRunning = true;
    	BizAgent.GRS_Proc_cnt = 0;
    	Smt_Proc.isRunning = false;
    	SMART_Proc.isRunning = false;
    	log.info(" GRS" + init_p.get("GRS") + ".");
    	
    	if(init_p.get("GRS").equals("1")) {
	    	Connection conn = null;
			
			try {
				conn = BizDBCPInit.getConnection();
				String upStr = "UPDATE cb_nano_broadcast_list SET proc_str = NULL WHERE proc_str IS NOT null";
				Statement updateExe = conn.createStatement();
				updateExe.execute(upStr);
				updateExe.close();
				conn.close();
				log.info("Nano Broadcast List 초기화 성공");
			} catch(Exception ex) {
				log.info("Nano Broadcast List 초기화 실패");
			}
		}
    	
        while(isRunning) {

			Date month = new Date();
			SimpleDateFormat transFormat = new SimpleDateFormat("yyyyMM");
			String monthStr = transFormat.format(month);

			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DATE, -1);
			String PremonthStr = transFormat.format(cal.getTime());

			isRunning = !isStop;
			
			if(!monthStr.equals(PreMonth))
			{
	        	// 매월 1일에는 Log Table 생성
	        	Create_LOG_Table clt = new Create_LOG_Table(DB_URL, log);
	        	clt.log = log;
	        	clt.monthStr = monthStr;
	        	Thread clt_proc = new Thread(clt);
	        	clt_proc.start();
	        	PreMonth = monthStr;
			}
			
        	// 2차 발신 분류 처리
			   
			for(int i=0; i<10; i++) 
			{
	        	TBLReqProcess trp = new TBLReqProcess(DB_URL, log, i);
	        	Thread trp_proc = new Thread(trp);
	        	if(!isStop)
	        		trp_proc.start();
	
	        	if(TBLReqProcess.isRunning[i])
	        		isRunning = true;
			}
			   
        	// 나노 아이티 동보 전송 처리
        	Nano_it_summary nano = new Nano_it_summary(DB_URL, log);
        	Thread nano_sum_proc = new Thread(nano);
        	if(!isStop)
        		nano_sum_proc.start();

        	if(Nano_it_summary.isRunning)
        		isRunning = true;

        	//IMC 동보 전송 필요시 주석 해제
//        	Imc_summary imc = new Imc_summary(DB_URL, log);
//        	Thread imc_sum_proc = new Thread(imc);
//        	if(!isStop)
//        		imc_sum_proc.start();
//
//        	if(Imc_summary.isRunning)
//        		isRunning = true;
        	
			// IMC  처리
        	if(init_p.get("IMC").equals("1")) {
	        	Imc_Proc imcproc = new Imc_Proc(DB_URL, log);
				Thread imc_proc = new Thread(imcproc);
				if(!isStop)
					imc_proc.start();
				if(Imc_Proc.isRunning)
	        		isRunning = true;
        	}

        	// SMT PHN 처리 
        	if(init_p.get("SMTPHN").equals("1")) {
	        	Smt_Proc smtproc = new Smt_Proc(DB_URL, log);
				Thread smt_proc = new Thread(smtproc);
				if(!isStop)
					smt_proc.start();
				if(Smt_Proc.isRunning)
	        		isRunning = true;
        	}
        	
        	// SMT PHN DB 직접 연결 처리 
        	if(init_p.get("SMTPHNDB").equals("1")) {
	        	SmtDB_Proc smtdbproc = new SmtDB_Proc(log);
	        	smtdbproc.isRefund = Boolean.parseBoolean( init_p.getProperty("REFUND") );
				Thread smtdb_proc = new Thread(smtdbproc);
				if(!isStop)
					smtdb_proc.start();
				if(SmtDB_Proc.isRunning)
	        		isRunning = true;
        	}
        	
			// Nano 폰문자 처리
        	if(init_p.get("PMS").equals("1")) {
	        	Nano_PMS_Proc nanoPMS = new Nano_PMS_Proc(DB_URL, log);
				nanoPMS.monthStr = monthStr;
				Thread nano_PMS_proc = new Thread(nanoPMS);
				if(!isStop)
					nano_PMS_proc.start();
	        	
	        	if(Nano_PMS_Proc.isRunning)
	        		isRunning = true;
	        	
	        	
				if(!monthStr.equals(PremonthStr)) {
		        	Nano_PMS_Proc PrenanoPMS = new Nano_PMS_Proc(DB_URL, log);
		        	PrenanoPMS.monthStr = PremonthStr;
		        	PrenanoPMS.isPremonth = true;
					Thread Prenano_PMS_proc = new Thread(PrenanoPMS);
					if(!isStop)
						Prenano_PMS_proc.start();
		        	
					if(Nano_PMS_Proc.isPreRunning)
		        		isRunning = true;
									
				}
        	}
        	
			// Nano FUN SMS 처리 ( GRS SMS )
        	if(init_p.get("FUN").equals("1")) {
	        	Nano_FUNSMS_Proc nanoFunsms = new Nano_FUNSMS_Proc(DB_URL, log);
	        	nanoFunsms.monthStr = monthStr;
				Thread nanoFunsms_proc = new Thread(nanoFunsms);
				if(!isStop)
					nanoFunsms_proc.start();
				if(Nano_FUNSMS_Proc.isRunning)
	        		isRunning = true;
	        	
				if(!monthStr.equals(PremonthStr)) {
					Nano_FUNSMS_Proc PrenanoFunsms = new Nano_FUNSMS_Proc(DB_URL, log);
		        	PrenanoFunsms.monthStr = PremonthStr;
		        	PrenanoFunsms.isPremonth = true;
					Thread PrenanoFunsms_proc = new Thread(PrenanoFunsms);
					if(!isStop)
						PrenanoFunsms_proc.start();
					if(Nano_FUNSMS_Proc.isPreRunning)
		        		isRunning = true;
				}
        	}
        	
			// Nano BKG LMS/MMS 처리
        	if(init_p.get("BKG").equals("1")) {
	        	Nano_BKGMMS_Proc nanoBkgmms = new Nano_BKGMMS_Proc(DB_URL, log);
	        	nanoBkgmms.monthStr = monthStr;
				Thread nanoBkgmms_proc = new Thread(nanoBkgmms);
				if(!isStop)
					nanoBkgmms_proc.start();
				if(Nano_BKGMMS_Proc.isRunning)
	        		isRunning = true;
				
				if(!monthStr.equals(PremonthStr)) {
					Nano_BKGMMS_Proc PrenanoBkgmms = new Nano_BKGMMS_Proc(DB_URL, log);
		        	PrenanoBkgmms.monthStr = PremonthStr;
		        	PrenanoBkgmms.isPremonth = true;
					Thread PrenanoBkgmms_proc = new Thread(PrenanoBkgmms);
					if(!isStop)
						PrenanoBkgmms_proc.start();
					if(Nano_BKGMMS_Proc.isPreRunning)
		        		isRunning = true;
				}
        	}
        	
			// Nano GRS 처리
			//for(int j=0; j<10; j++)
        	if(init_p.get("GRS").equals("1")) {
				Nano_GRS_Proc nanogrs = new Nano_GRS_Proc(DB_URL, log);
				nanogrs.monthStr = monthStr;
				nanogrs.isRefund = Boolean.parseBoolean( init_p.getProperty("REFUND") );
				Thread nanogrs_proc = new Thread(nanogrs);
				nanogrs_proc.start();
				
				if(!monthStr.equals(PremonthStr)) {
					Nano_GRS_Proc Prenanogrs = new Nano_GRS_Proc(DB_URL, log);
					Prenanogrs.monthStr = PremonthStr;
					Prenanogrs.isPremonth = true;
					Thread Prenanogrs_proc = new Thread(Prenanogrs);
					Prenanogrs_proc.start();
				}
        	}

			// Smart Me 처리
        	if(init_p.get("SMT").equals("1")) {
				SMART_Proc smt = new SMART_Proc(log);
				smt.monthStr = monthStr;
				smt.isRefund = Boolean.parseBoolean( init_p.getProperty("REFUND") );
				Thread smt_proc = new Thread(smt);
				smt_proc.start();
				
				if(!monthStr.equals(PremonthStr)) {
					SMART_Proc Presmt = new SMART_Proc(log);
					Presmt.monthStr = PremonthStr;
					Thread Presmt_proc = new Thread(Presmt);
					Presmt_proc.start();
				}
        	}
        	
			// Naself SMS 처리
        	if(init_p.get("NAS").equals("1")) {
				NAS_SMS_Proc nassms = new NAS_SMS_Proc(DB_URL, log);
				nassms.monthStr = monthStr;
				nassms.isRefund = Boolean.parseBoolean( init_p.getProperty("REFUND") );
				Thread nassms_proc = new Thread(nassms);
				if(!isStop)
					nassms_proc.start();
				if(NAS_SMS_Proc.isRunning)
	        		isRunning = true;
				
				if(!monthStr.equals(PremonthStr)) {
					NAS_SMS_Proc Prenassms = new NAS_SMS_Proc(DB_URL, log);
					Prenassms.monthStr = PremonthStr;
					Prenassms.isPremonth = true;
					Thread Prenassms_proc = new Thread(Prenassms);
					if(!isStop)
						Prenassms_proc.start();
					if(NAS_SMS_Proc.isPreRunning)
		        		isRunning = true;
				}
				
				// Naself MMS 처리
				NAS_MMS_Proc nasmms = new NAS_MMS_Proc(DB_URL, log);
				nasmms.monthStr = monthStr;
				nasmms.isRefund = Boolean.parseBoolean( init_p.getProperty("REFUND") );
				Thread nasmms_proc = new Thread(nasmms);
				if(!isStop)
					nasmms_proc.start();
				if(NAS_MMS_Proc.isRunning)
	        		isRunning = true;
				
				if(!monthStr.equals(PremonthStr)) {
					NAS_MMS_Proc Prenasmms = new NAS_MMS_Proc(DB_URL, log);
					Prenasmms.monthStr = PremonthStr;
					Prenasmms.isPremonth = true;
					Thread Prenasmms_proc = new Thread(Prenasmms);
					if(!isStop)
						Prenasmms_proc.start();
					if(NAS_MMS_Proc.isPreRunning)
		        		isRunning = true;
				}
        	}
        	
            try {
                //log.info("Biz Agent Call OK.");
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                log.info("메인 Thread 오류 : " + e.toString());
            }
                        
           // if (no > 1) {
            	//log.info("Biz Agent 끝.");
                //break;
           // }
           // no++;
        }
    }
}
