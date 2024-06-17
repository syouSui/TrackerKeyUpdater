package dontae.trackerkeyupdater;

import com.fasterxml.jackson.databind.ObjectMapper;
import dontae.trackerkeyupdater.po.Response;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.*;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TrackerKeyUpdaterApplication {
    private static final RestTemplate restTemplate          = new RestTemplate();
    private static final ObjectMapper objectMapper          = new ObjectMapper();
    private static final String       SUCCESS_FLAG          = "success";
    private static final String       ERROR_TRACKER_CONTENT = "Tracker gave HTTP response code 403 (Forbidden)";
    private static       HttpHeaders  headers               = new HttpHeaders();
    private static       String       url;
    private static       String       passkey;
    private static       String       username;
    private static       String       password;
    private static       String       authorization;
    private static       String       sessionId;

    private static final Predicate<ResponseEntity<Response>> isResponseOk =
            response -> HttpStatus.OK.equals(response.getStatusCode()) &&
                        ObjectUtils.isNotEmpty(response.getBody()) &&
                        SUCCESS_FLAG.equals(response.getBody().getResult());

    private static String generateFormData(Response.Arguments.Torrent torrent, String passkey) {
        String tracker = torrent.getTrackerStats().get(0).getAnnounce();
        return String.format("""
                                     {"method":"torrent-set","arguments":{"ids":%d,"trackerReplace":[0,"%s?passkey=%s"]},"tag":""}
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

    private static Boolean modifyAnnounceByRest(String url, String payload) {
        ResponseEntity<Response> responseEntity = restTemplate.postForEntity(
                url,
                new HttpEntity<>(payload, headers),
                Response.class);
        return isResponseOk.test(responseEntity);
    }

    private static void handler(String url, String passkey) {
        listTorrents(url).parallelStream()
                         .filter(t -> ERROR_TRACKER_CONTENT.equals(t.getErrorString()))
                         .forEach(t -> {
                             Boolean res = modifyAnnounceByRest(url, generateFormData(t, passkey));
                             System.out.println(res + " " + t.getName());
                         });
    }

    private static void init(String[] args) {
        Consumer<String[]> argsInitFun = s -> {
            try {
                CommandLine cmd = new DefaultParser()
                        .parse(new Options().addOption("url", true, "URL参数")
                                            .addOption("passkey", true, "passkey")
                                            .addOption("username", true, "用户参数")
                                            .addOption("password", true, "密码参数")
                                            .addOption("Authorization", true, "Authorization")
                                            .addOption("XTransmissionSessionId", true, "X-Transmission-Session-Id"),
                               args);
                url           = cmd.getOptionValue("url");
                passkey       = cmd.getOptionValue("passkey");
                username      = cmd.getOptionValue("username");
                password      = cmd.getOptionValue("password");
                authorization = cmd.getOptionValue("Authorization");
                sessionId     = cmd.getOptionValue("XTransmissionSessionId");
            } catch (ParseException e) {
                System.out.println("命令行参数解析失败: " + e);
            }
        };
        Runnable restInitFun = () -> {
            restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(username, password));

            headers.set("Authorization", authorization);
            headers.set("XMLHttpRequest", "XMLHttpRequest");
            headers.set("X-Transmission-Session-Id", sessionId);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        };
        argsInitFun.accept(args);
        restInitFun.run();
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(args));

        init(args);

        handler(url, passkey);
    }
}
