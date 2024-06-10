package vip.acrocyber.trackerkeyupdater;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.*;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestTemplate;
import vip.acrocyber.trackerkeyupdater.po.Response;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;


public class TrackerKeyUpdaterApplication {
    private static final RestTemplate restTemplate          = new RestTemplate();
    private static final ObjectMapper objectMapper          = new ObjectMapper();
    private static       HttpHeaders  headers               = new HttpHeaders();
    private static final String       SUCCESS_FLAG          = "success";
    private static final String       ERROR_TRACKER_CONTENT = "Tracker gave HTTP response code 403 (Forbidden)";

    private static final Predicate<ResponseEntity<Response>> isResponseOk =
            response -> HttpStatus.OK.equals(response.getStatusCode()) &&
                        ObjectUtils.isNotEmpty(response.getBody()) &&
                        SUCCESS_FLAG.equals(response.getBody().getResult());

    private static String generateFormData(Response.Arguments.Torrent torrent, String passkey) {
        String tracker = torrent.getTrackerStats().get(0).getAnnounce();
        return String.format("""
                                     {"method":"torrent-set","arguments":{"ids":%d,"trackerReplace":[0,"%s?passkey: %s"]},"tag":""}
                                     """,
                             torrent.getId(),
                             tracker.substring(0, tracker.indexOf("?")),
                             passkey);
    }

    private static List<Response.Arguments.Torrent> listTorrents(String url) {
        ResponseEntity<Response> responseEntity = restTemplate.postForEntity(
                url,
                new HttpEntity<>("""
                                         {"method":"torrent-get","arguments":{"fields":["id","name","status","hashString","totalSize","percentDone","addedDate","trackerStats","leftUntilDone","rateDownload","rateUpload","recheckProgress","rateDownload","rateUpload","peersGettingFromUs","peersSendingToUs","uploadRatio","uploadedEver","downloadedEver","downloadDir","error","errorString","doneDate","queuePosition","activityDate"]},"tag":""}
                                         """,
                                 headers),
                Response.class);
        return isResponseOk.test(responseEntity) ?
                responseEntity.getBody().getArguments().getTorrents() :
                List.of();
    }

    private static Boolean modifyAnnounce(String url, String payload) {
        ResponseEntity<Response> responseEntity = restTemplate.postForEntity(
                url,
                new HttpEntity<>(payload, headers),
                Response.class);
        // TODO invalid argument
        return isResponseOk.test(responseEntity);
    }

    private static void handler(String url, String passkey) {
        listTorrents(url).parallelStream()
                         .filter(t -> ERROR_TRACKER_CONTENT.equals(t.getErrorString()))
                         .forEach(t -> {
                             Boolean res = modifyAnnounce(url, generateFormData(t, passkey));
                             System.out.println(res + " " + t.getName());
                         });
    }

    private static void runner(String[] args) {
        try {
            CommandLine cmd = new DefaultParser()
                    .parse(new Options().addOption("url", true, "URL参数")
                                        .addOption("passkey", true, "passkey")
                                        .addOption("username", true, "用户参数")
                                        .addOption("password", true, "密码参数"),
                           args);

            String url      = cmd.getOptionValue("url");
            String passkey  = cmd.getOptionValue("passkey");
            String username = cmd.getOptionValue("username");
            String password = cmd.getOptionValue("password");

            restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(username, password));

            handler(url, passkey);

        } catch (ParseException e) {
            System.out.println("命令行参数解析失败: " + e);
        }
    }

    private static void init() {
//            headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(password.getBytes()));
        headers.set("Authorization", "Basic UFQ2NjY2NjY6UFQ2NjY2NjY=");
        headers.set("XMLHttpRequest", "XMLHttpRequest");
        headers.set("X-Transmission-Session-Id", "Z4kYQsAqH0Qal7gdGls7XYtrjvyDMLNlCVvsgnSYqnL347J8");
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    }

    public static void main(String[] args) {
        // TODO temp args
        args = new String[]{
                "-url", "https://tr.acrocyber.vip:8/transmission/rpc",
                "-passkey", "968d343a2b8641c64bb1dedb134e4323",
                "-username", "PT666666",
                "-password", "PT666666"};

        init();

        System.out.println(Arrays.toString(args));

        runner(args);
    }
}
