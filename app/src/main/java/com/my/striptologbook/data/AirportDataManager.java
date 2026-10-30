package com.my.striptologbook.data;

import android.content.Context;
import android.util.Log;

import com.my.striptologbook.R;
import com.my.striptologbook.utils.UnsafeHttpClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AirportDataManager {

    private static final String TAG = "AirportDataManager";
    private static final String JSON_FILENAME = "airports.json";
    private static final String CACHE_FILENAME = "airports.cache";

    private final Context appContext;
    private Map<String, Airport> iataAirportMap = new HashMap<>();
    private Map<String, Airport> icaoAirportMap = new HashMap<>();

    private int loadedAirportCount = 0;
    private int skippedAirportCount = 0;

    private static AirportDataManager instance;

    private AirportDataManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized AirportDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new AirportDataManager(context);
        }
        return instance;
    }

    // ----- Getters -----
    public int getLoadedAirportCount() { return loadedAirportCount; }
    public int getSkippedAirportCount() { return skippedAirportCount; }

    // ----- Main download (JSON) -----
    public boolean downloadAndLoadDatabase() {
        String jsonUrl = appContext.getString(R.string.airports_json_url);
        OkHttpClient client = UnsafeHttpClient.getUnsafeOkHttpClient();
        Request request = new Request.Builder().url(jsonUrl).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                android.util.Log.e(TAG, "HTTP error: " + response.code());
                return false;
            }

            File dest = new File(appContext.getFilesDir(), JSON_FILENAME);
            try (InputStream input = response.body().byteStream();
                 FileOutputStream output = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            } catch (IOException e) {
                android.util.Log.e(TAG, "Error saving JSON", e);
                return false;
            }

            clearCache();
            loadData();
            return true;

        } catch (IOException e) {
            android.util.Log.e(TAG, "Network error", e);
            return false;
        }
    }

    // ----- Public finder -----
    public Airport findAirportByCode(String code) {
        if (iataAirportMap.isEmpty() && icaoAirportMap.isEmpty()) {
            loadData();
        }
        if (code == null || code.isEmpty()) return null;
        code = code.toUpperCase().trim();
        if (code.length() == 3) return iataAirportMap.get(code);
        else if (code.length() == 4) return icaoAirportMap.get(code);
        return null;
    }

    public synchronized void ensureDataLoaded() {
        if (iataAirportMap.isEmpty() && icaoAirportMap.isEmpty()) {
            loadData();
        }
    }

    public void clearCache() {
        iataAirportMap.clear();
        icaoAirportMap.clear();
        loadedAirportCount = 0;
        skippedAirportCount = 0;
        File cacheFile = new File(appContext.getFilesDir(), CACHE_FILENAME);
        if (cacheFile.exists()) cacheFile.delete();
    }

    // ----- Loading orchestration -----
//    private void loadData() {
//        if (loadFromSerializedCache()) return;
//        // Ensure the internal JSON file exists (copy from assets if needed)
//        copyDefaultDatabaseIfNeeded();
//        if (loadFromJson()) {
//            saveToSerializedCache();
//        }
//
//    }

    private void loadData() {
        copyDefaultDatabaseIfNeeded();
        if (loadFromSerializedCache()) {
            return;
        }
        if (loadFromJson()) {
            applyUserModifications();
            saveToSerializedCache();
        }
    }
    // ----- Serialised cache -----
    @SuppressWarnings("unchecked")
    private boolean loadFromSerializedCache() {
        File cacheFile = new File(appContext.getFilesDir(), CACHE_FILENAME);
        if (!cacheFile.exists()) return false;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cacheFile))) {
            iataAirportMap = (HashMap<String, Airport>) ois.readObject();
            icaoAirportMap = (HashMap<String, Airport>) ois.readObject();
            loadedAirportCount = 0;
            skippedAirportCount = 0;
            return true;
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            android.util.Log.e(TAG, "Failed to load cache", e);
            iataAirportMap.clear();
            icaoAirportMap.clear();
            cacheFile.delete();
            return false;
        }
    }

    private void saveToSerializedCache() {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(new File(appContext.getFilesDir(), CACHE_FILENAME)))) {
            oos.writeObject(iataAirportMap);
            oos.writeObject(icaoAirportMap);
        } catch (IOException e) {
            android.util.Log.e(TAG, "Failed to save cache", e);
        }
    }

    // ----- JSON loading -----

    private void copyDefaultDatabaseIfNeeded() {
        File dest = new File(appContext.getFilesDir(), JSON_FILENAME);
        if (dest.exists()) return;
        try (InputStream in = appContext.getAssets().open(JSON_FILENAME);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            android.util.Log.d(TAG, "Default database copied to internal storage");
        } catch (IOException e) {
            android.util.Log.e(TAG, "Failed to copy default database", e);
        }
    }

    private boolean loadFromJson() {
        iataAirportMap.clear();
        icaoAirportMap.clear();
        loadedAirportCount = 0;
        skippedAirportCount = 0;

        try (InputStream is = appContext.openFileInput(JSON_FILENAME);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            String jsonString = sb.toString();

            JSONObject root = new JSONObject(jsonString);
            Iterator<String> keys = root.keys();

            int loaded = 0;
            int skipped = 0;

            while (keys.hasNext()) {
                String icao = keys.next();
                JSONObject airportJson = root.getJSONObject(icao);

                String iata = airportJson.optString("iata", "").trim();
                if (iata.isEmpty()) {
                    skipped++;
                    continue;
                }

                String name = airportJson.optString("name", "Unknown");
                String city = airportJson.optString("city", "");
                String state = airportJson.optString("state", "");
                String country = airportJson.optString("country", "");
                double lat = airportJson.has("lat") ? airportJson.optDouble("lat", Double.NaN) : Double.NaN;
                double lon = airportJson.has("lon") ? airportJson.optDouble("lon", Double.NaN) : Double.NaN;
                double elevation = airportJson.has("elevation") ? airportJson.optDouble("elevation", 0.0) : 0.0;
                String timezone = airportJson.optString("tz", "");

                Airport airport = new Airport(
                        name, city, state, country,
                        iata, icao,
                        lat, lon, elevation,
                        timezone
                );

                iataAirportMap.put(iata.toUpperCase(), airport);
                icaoAirportMap.put(icao.toUpperCase(), airport);
                loaded++;
            }

            loadedAirportCount = loaded;
            skippedAirportCount = skipped;
            return true;

        } catch (IOException | JSONException e) {
            android.util.Log.e(TAG, "Failed to load JSON", e);
            return false;
        }
    }

    public List<String> searchAirportCodes(String query) {
        if (iataAirportMap.isEmpty() && icaoAirportMap.isEmpty()) {
            loadData();
        }
        if (query == null || query.isEmpty()) return Collections.emptyList();
        String upperQuery = query.toUpperCase().trim();
        List<String> results = new ArrayList<>();
        for (String code : icaoAirportMap.keySet()) {
            if (code.startsWith(upperQuery)) {
                results.add(code);
            }
        }
        Collections.sort(results);
        return results;
    }

    // ----- NEW Airport class (without continent) -----
    public static class Airport implements Serializable {
        private static final long serialVersionUID = 2L; // force cache invalidation
        public String name, city, state, country;
        public String iata, icao;
        public double latitude, longitude, elevation;
        public String timezone;

        public Airport(String name, String city, String state, String country,
                       String iata, String icao,
                       double latitude, double longitude, double elevation,
                       String timezone) {
            this.name = name;
            this.city = city;
            this.state = state;
            this.country = country;
            this.iata = iata;
            this.icao = icao;
            this.latitude = latitude;
            this.longitude = longitude;
            this.elevation = elevation;
            this.timezone = timezone;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append("\n");
            sb.append(iata).append(" - ").append(icao).append("\n");
            if (!state.isEmpty()) {
                sb.append(city).append(", ").append(state).append(" - ").append(country).append("\n");
            } else {
                sb.append(city).append(" - ").append(country).append("\n");
            }
            sb.append("Elevation: ").append(Math.round(elevation)).append(" ft\n");
            if (!timezone.isEmpty()) {
                sb.append("Timezone: ").append(timezone).append("\n");
            }
            return sb.toString();
        }
    }

    // ========== NEW: User modification support ==========

    private static final String USER_JSON_FILENAME = "airports_user.json";
    private final Set<String> modifiedIcaoCodes = new HashSet<>();
    private final Set<String> deletedIcaoCodes = new HashSet<>();

    private void applyUserModifications() {
        File userFile = new File(appContext.getFilesDir(), USER_JSON_FILENAME);
        if (!userFile.exists()) return;

        try (FileInputStream fis = new FileInputStream(userFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject root = new JSONObject(sb.toString());

            modifiedIcaoCodes.clear();
            deletedIcaoCodes.clear();

            JSONArray updated = root.optJSONArray("updated");
            if (updated != null) {
                for (int i = 0; i < updated.length(); i++) {
                    JSONObject obj = updated.getJSONObject(i);
                    Airport airport = parseAirportFromJson(obj);
                    if (airport != null && airport.icao != null && !airport.icao.isEmpty()) {
                        String icao = airport.icao.toUpperCase();
                        Airport existing = icaoAirportMap.get(icao);
                        if (existing != null && existing.iata != null) {
                            iataAirportMap.remove(existing.iata.toUpperCase());
                        }
                        icaoAirportMap.put(icao, airport);
                        if (airport.iata != null && !airport.iata.isEmpty()) {
                            iataAirportMap.put(airport.iata.toUpperCase(), airport);
                        }
                        modifiedIcaoCodes.add(icao);
                    }
                }
            }

            JSONArray deleted = root.optJSONArray("deleted");
            if (deleted != null) {
                for (int i = 0; i < deleted.length(); i++) {
                    String icao = deleted.getString(i);
                    if (icao != null && !icao.isEmpty()) {
                        icao = icao.toUpperCase();
                        Airport removed = icaoAirportMap.remove(icao);
                        if (removed != null && removed.iata != null) {
                            iataAirportMap.remove(removed.iata.toUpperCase());
                        }
                        deletedIcaoCodes.add(icao);
                        modifiedIcaoCodes.remove(icao);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply user modifications", e);
        }
    }

    private Airport parseAirportFromJson(JSONObject obj) {
        String name = obj.optString("name", "Unknown");
        String city = obj.optString("city", "");
        String state = obj.optString("state", "");
        String country = obj.optString("country", "");
        String iata = obj.optString("iata", "");
        String icao = obj.optString("icao", "");
        double lat = obj.has("lat") ? obj.optDouble("lat", Double.NaN) : Double.NaN;
        double lon = obj.has("lon") ? obj.optDouble("lon", Double.NaN) : Double.NaN;
        double elevation = obj.has("elevation") ? obj.optDouble("elevation", 0.0) : 0.0;
        String tz = obj.optString("tz", "");
        return new Airport(name, city, state, country, iata, icao, lat, lon, elevation, tz);
    }

    private void saveUserModifications() {
        try {
            JSONObject root = new JSONObject();
            JSONArray updatedArray = new JSONArray();
            for (String icao : modifiedIcaoCodes) {
                Airport airport = icaoAirportMap.get(icao);
                if (airport != null) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", airport.name != null ? airport.name : "");
                    obj.put("city", airport.city != null ? airport.city : "");
                    obj.put("state", airport.state != null ? airport.state : "");
                    obj.put("country", airport.country != null ? airport.country : "");
                    obj.put("iata", airport.iata != null ? airport.iata : "");
                    obj.put("icao", airport.icao != null ? airport.icao : "");
                    obj.put("lat", airport.latitude);
                    obj.put("lon", airport.longitude);
                    obj.put("elevation", airport.elevation);
                    obj.put("tz", airport.timezone != null ? airport.timezone : "");
                    updatedArray.put(obj);
                }
            }
            JSONArray deletedArray = new JSONArray();
            for (String icao : deletedIcaoCodes) {
                deletedArray.put(icao);
            }
            root.put("updated", updatedArray);
            root.put("deleted", deletedArray);

            File userFile = new File(appContext.getFilesDir(), USER_JSON_FILENAME);
            try (FileOutputStream fos = new FileOutputStream(userFile)) {
                fos.write(root.toString().getBytes());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save user modifications", e);
        }
    }

    // ----- Public editor methods -----
    public List<Airport> searchAirports(String query) {
        List<Airport> results = new ArrayList<>();
        if (query == null || query.isEmpty()) return results;
        String lowerQuery = query.toLowerCase().trim();
        if (iataAirportMap.isEmpty() && icaoAirportMap.isEmpty()) loadData();

        Set<Airport> unique = new HashSet<>();
        unique.addAll(iataAirportMap.values());
        unique.addAll(icaoAirportMap.values());

        for (Airport airport : unique) {
            if (airport.iata != null && airport.iata.toLowerCase().contains(lowerQuery) ||
                    airport.icao != null && airport.icao.toLowerCase().contains(lowerQuery) ||
                    airport.city != null && airport.city.toLowerCase().contains(lowerQuery) ||
                    airport.name != null && airport.name.toLowerCase().contains(lowerQuery)) {
                results.add(airport);
            }
        }
        results.sort(Comparator.comparing(a -> a.iata != null ? a.iata : ""));
        return results;
    }

    public boolean addAirport(Airport airport) {
        if (airport.icao == null || airport.icao.isEmpty()) return false;
        if (airport.iata == null || airport.iata.isEmpty()) return false;
        if (airport.name == null || airport.name.isEmpty()) return false;
        String icao = airport.icao.toUpperCase();
        String iata = airport.iata.toUpperCase();
        if (icaoAirportMap.containsKey(icao)) return false;
        if (iataAirportMap.containsKey(iata)) return false;

        icaoAirportMap.put(icao, airport);
        iataAirportMap.put(iata, airport);
        modifiedIcaoCodes.add(icao);
        deletedIcaoCodes.remove(icao);
        saveUserModifications();
        saveToSerializedCache();
        return true;
    }

    public boolean updateAirport(Airport airport) {
        if (airport.icao == null || airport.icao.isEmpty()) return false;
        String icao = airport.icao.toUpperCase();
        if (!icaoAirportMap.containsKey(icao)) return false;

        Airport existing = icaoAirportMap.get(icao);
        String oldIata = existing.iata;
        String newIata = airport.iata.toUpperCase();
        if (!oldIata.equals(newIata)) {
            iataAirportMap.remove(oldIata);
            if (iataAirportMap.containsKey(newIata)) return false;
            iataAirportMap.put(newIata, airport);
        }
        icaoAirportMap.put(icao, airport);
        if (oldIata.equals(newIata)) {
            iataAirportMap.put(newIata, airport);
        }
        modifiedIcaoCodes.add(icao);
        deletedIcaoCodes.remove(icao);
        saveUserModifications();
        saveToSerializedCache();
        return true;
    }

    public boolean deleteAirport(String icao) {
        if (icao == null || icao.isEmpty()) return false;
        icao = icao.toUpperCase();
        Airport airport = icaoAirportMap.remove(icao);
        if (airport == null) return false;
        if (airport.iata != null) iataAirportMap.remove(airport.iata.toUpperCase());
        deletedIcaoCodes.add(icao);
        modifiedIcaoCodes.remove(icao);
        saveUserModifications();
        saveToSerializedCache();
        return true;
    }

    public void clearUserModifications() {
        File userFile = new File(appContext.getFilesDir(), USER_JSON_FILENAME);
        if (userFile.exists()) userFile.delete();
        modifiedIcaoCodes.clear();
        deletedIcaoCodes.clear();
        loadData();
    }

    public void deleteAllAirportData() {
        new File(appContext.getFilesDir(), JSON_FILENAME).delete();
        new File(appContext.getFilesDir(), USER_JSON_FILENAME).delete();
        new File(appContext.getFilesDir(), CACHE_FILENAME).delete();
        iataAirportMap.clear();
        icaoAirportMap.clear();
        modifiedIcaoCodes.clear();
        deletedIcaoCodes.clear();
        loadedAirportCount = 0;
        skippedAirportCount = 0;
    }

    public Set<Airport> getAllAirports() {
        Set<Airport> all = new HashSet<>();
        all.addAll(iataAirportMap.values());
        all.addAll(icaoAirportMap.values());
        return all;
    }

    public void ensureInitialDatabase() {
        copyDefaultDatabaseIfNeeded();
    }

}