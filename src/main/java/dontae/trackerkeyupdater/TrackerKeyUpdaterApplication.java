package dontae.trackerkeyupdater;

import dontae.trackerkeyupdater.po.Response;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.*;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TrackerKeyUpdaterApplication {
    private static final RestTemplate restTemplate          = new RestTemplate();
    private static final String       SUCCESS_FLAG          = "success";
    private static final String       ERROR_TRACKER_CONTENT = "Tracker gave HTTP response code 403 (Forbidden)";
    private static       HttpHeaders  headers               = new HttpHeaders();
    private static       String       url;
    private static       String       passkey;
    private static       String       username;
    private static       String       password;
    private static       String       sessionId;

    private static final Predicate<ResponseEntity<Response>> isResponseOk =
            response -> HttpStatus.OK.equals(response.getStatusCode()) &&
                        ObjectUtils.isNotEmpty(response.getBody()) &&
                        SUCCESS_FLAG.equals(response.getBody().getResult());

    /**
     * 构造修改的请求参数
     *
     * @param torrent 种子
     * @param passkey passkey
     * @return 构造修改参数
     */
    private static String generateFormData(Response.Arguments.Torrent torrent, String passkey) {
        String tracker = torrent.getTrackerStats().get(0).getAnnounce();
        return String.format("""
                                     {"method":"torrent-set","arguments":{"ids":%d,"trackerReplace":[0,"%s?passkey=%s"]},"tag":""}
                                     """,
                             torrent.getId(),
                             tracker.substring(0, tracker.indexOf("?")),
                             passkey);
    }

    /**
     * 查询种子列表
     *
     * @param url rpc地址
     * @return 种子对象列表
     */
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

    /**
     * 修改逻辑
     *
     * @param url     rpc地址
     * @param payload post表单参数
     * @return 是否修改成功
     */
    private static Boolean modifyAnnounceByRest(String url, String payload) {
        ResponseEntity<Response> responseEntity = restTemplate.postForEntity(
                url,
                new HttpEntity<>(payload, headers),
                Response.class);
        return isResponseOk.test(responseEntity);
    }

    /**
     * 调用处理逻辑
     *
     * @param url     rpc地址
     * @param passkey 新的密钥
     */
    private static void handler(String url, String passkey) {
        listTorrents(url).parallelStream()
                         .filter(t -> ERROR_TRACKER_CONTENT.equals(t.getErrorString()))
                         .forEach(t -> {
                             Boolean res = modifyAnnounceByRest(url, generateFormData(t, passkey));
                             System.out.println(res + " " + t.getName());
                         });
    }

    /**
     * 初始化请请求头, 重点是 X-Transmission-Session-Id
     *
     * @param url rpc地址
     */
    private static void initHeaders(String url) {
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(username, password));
        headers.set("XMLHttpRequest", "XMLHttpRequest");
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            restTemplate.postForObject(url,
                                       new HttpEntity<>("", headers),
                                       String.class);
        } catch (Exception e) {
            if (e.getMessage().contains("409 Conflict")) {
                String $sessionId = e.getMessage()
                                     .substring(e.getMessage().indexOf("X-Transmission-Session-Id: ") + 27,
                                                e.getMessage().indexOf("</code>"));
                System.out.println("Extract X-Transmission-Session-Id is: " + $sessionId);
                sessionId = $sessionId;
            } else {
                throw e;
            }
        }

        headers.set("X-Transmission-Session-Id", sessionId);
    }

    /**
     * 读取参数
     *
     * @param args Java -jar 传入的参数
     */
    private static void initArgs(String[] args) {
        Consumer<String[]> argsInitFun = s -> {
            try {
                CommandLine cmd = new DefaultParser()
                        .parse(new Options().addOption("url", true, "URL参数")
                                            .addOption("passkey", true, "passkey")
                                            .addOption("username", true, "用户参数")
                                            .addOption("password", true, "密码参数"),
                               args);
                url      = cmd.getOptionValue("url");
                passkey  = cmd.getOptionValue("passkey");
                username = cmd.getOptionValue("username");
                password = cmd.getOptionValue("password");
            } catch (ParseException e) {
                System.out.println("命令行参数解析失败: " + e);
            }
        };
        argsInitFun.accept(args);
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(args));

        initArgs(args);

        initHeaders(url);

        handler(url, passkey);
    }
}
