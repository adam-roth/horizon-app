package au.com.suncoastpc.horizonsync.net;

public enum ApiContext {
    LIVE_MUSIC("Event/Music/Live");

    public static final String STANDARD_MUSIC_SEARCH_PARAMS = "location=84&category=6&ClassificationID=24&CountryID=1&StateID=2&CityID=24";

    private String name;

    private ApiContext(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
