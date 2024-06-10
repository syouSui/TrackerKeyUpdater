package dontae.trackerkeyupdater.po;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author : Dontae
 * @Version : 1.0.0
 * @CreateTime : 2024/6/10 15:03
 * @Description :
 */

@Accessors(chain = true)
@Data
public class Response {
    private Arguments arguments;
    private String result;

    @Accessors(chain = true)
    @Data
    public static class Arguments {
        private List<Torrent> torrents;

        @Accessors(chain = true)
        @Data
        public static class Torrent {
            private int id;
            private String name;
            private String errorString;
            private List<TrackerStats> trackerStats;

            @Accessors(chain = true)
            @Data
            public static class TrackerStats {
                private String lastAnnounceResult;
                private String announce;
            }
        }
    }
}