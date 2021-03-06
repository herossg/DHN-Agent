package com.dhn.client.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.dhn.client.model.DhnRequest;
import com.dhn.client.model.DhnResult;
import com.dhn.client.model.UserInfo;
import com.dhn.client.service.DhnRequestService;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SendRequest {
	
	
	private static DhnRequestService dhnReqService ;
	
	public static boolean isRunning = false;
	private static final Logger log = LoggerFactory.getLogger(SendRequest.class);
	private static Environment env;
	private static int totalcnt;
	
	@Autowired
	public SendRequest(DhnRequestService dhnReqService, Environment env) {
		SendRequest.dhnReqService = dhnReqService;
		SendRequest.env = env;
	}
	
	public static void login() {
		final String URL =  env.getProperty("server") + "login";
		HttpHeaders headers = new HttpHeaders();

		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("userid", env.getProperty("userid"));
		
		RestTemplate restTemp = new RestTemplate();
		ObjectMapper mapper = new ObjectMapper();
		mapper.setSerializationInclusion(Include.NON_NULL);

		UserInfo ui = new UserInfo();
		ui.userid = env.getProperty("userid");
		ui.port = env.getProperty("server.port");
		
		String jsonStr;
		try {
			jsonStr = mapper.writeValueAsString(ui);
			//log.info("Req Str : " + jsonStr);
			
			HttpEntity<String> entity = new HttpEntity<String>(jsonStr,headers);						
			ResponseEntity<String> response = restTemp.postForEntity(URL, entity, String.class ); 
			
			Map<String, String> res;
			JsonNode json = mapper.readTree(response.getBody());
			
			log.info("Log in ...." + json.get("rescode").asText().toUpperCase());
			if(json.get("rescode").asText().toUpperCase().equals("OK")) {
				DhnController.isStart = true;
			}
				
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void run() {
		if(!isRunning) {
			SimpleDateFormat format1 = new SimpleDateFormat ( "yyyyMMddHHmmss");
			Date time = new Date();
			String starttime = format1.format(time);
			//log.info(starttime + " - 시작");
			totalcnt =0;
			try {
				isRunning = true;
				List<DhnRequest> dhnReqs = null;
				if(env.getProperty("dbms").toUpperCase().equals("MYSQL")) {
					dhnReqs = dhnReqService.selectByReserveQuery();
				} else if(env.getProperty("dbms").toUpperCase().equals("ORACLE")) {
					dhnReqs = dhnReqService.selectByReserveQuery_oracle();
				}
				//log.info("불러온 자료 : " + dhnReqs.size());
				if(dhnReqs != null && dhnReqs.size() > 0) {
					log.info(starttime + " - 시작");
					final String URL =  env.getProperty("server") + "req";
					HttpHeaders headers = new HttpHeaders();

					headers.setContentType(MediaType.APPLICATION_JSON);
					headers.set("userid", env.getProperty("userid"));
					
					RestTemplate restTemp = new RestTemplate();
					ObjectMapper mapper = new ObjectMapper();
					mapper.setSerializationInclusion(Include.NON_NULL);
					
					
					String jsonStr = mapper.writeValueAsString(dhnReqs);
					
					//log.info("Req Str : " + jsonStr);
					
					HttpEntity<String> entity = new HttpEntity<String>(jsonStr,headers);						
					ResponseEntity<List> response = restTemp.postForEntity(URL, entity, List.class ); 
					
					dhnReqService.deleteByInMsgidQuery(response.getBody());
					
					totalcnt = totalcnt + dhnReqs.size();

				}
			} catch (Exception e) {
				log.info(starttime + " / " + e.toString());
				try {
					Thread.sleep(60000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			} finally {
				isRunning = false;	
			}
			
			if(totalcnt > 0)
				log.info(starttime + " - 끝 > " + totalcnt + " 건  발송 완료. ");
		}
	}
}
