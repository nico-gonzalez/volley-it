package com.infuy.blog.volleypost;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.http.HttpResponseCache;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Cache;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.NetworkImageView;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.CacheResponse;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Scanner;


public class MainActivity extends ActionBarActivity {

    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String RANDOM_URL = "http://api.icndb.com/jokes/random";
    public static final String IMAGE_URL = "http://www.los3dragones.com/imagenes/chucknorris/norris1.jpg";

    private TextView mJokeTextView;
    private ImageView mHttpUrlConnectionImageView;
    private ImageView mVolleyImageView;
    private ImageView mVolleyImageLoaderImageView;
    private NetworkImageView mNetworkImageView;

    private RequestQueue mRequestQueue;

    private ImageLoader mImageLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mJokeTextView = (TextView) findViewById(R.id.joke);

        // this is only for the sake of this example. Is common practice to use
        // a singleton to instantiate this once on a global scope
        mRequestQueue = Volley.newRequestQueue(this.getApplicationContext());

        mHttpUrlConnectionImageView = (ImageView) findViewById(R.id.httpurlconnection_image_view);
        new HttpURLConnectionImageAsyncTask(mHttpUrlConnectionImageView).execute(IMAGE_URL);

        mVolleyImageView = (ImageView) findViewById(R.id.volley_image_request_view);

        // you can use an image request and set the downloaded image on the onResponse callback
        // or set an error image or placeholder when the call fails
        ImageRequest imageRequest = new ImageRequest(IMAGE_URL, new Response.Listener<Bitmap>() {
            @Override
            public void onResponse(Bitmap response) {

                mVolleyImageView.setImageBitmap(response);
            }
        },
        0,
        0,
        ImageView.ScaleType.CENTER_CROP,
        null,
        new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // show an error drawable
                mVolleyImageView.setImageDrawable(getDrawable(R.drawable.error));
            }
        }){
            @Override
            public Priority getPriority(){
                return Priority.LOW;
            }
        };

        // add the ImageRequest to the Volleys request queue
        mRequestQueue.add(imageRequest);

        // You can also user an ImageLoader with your custom ImageCache implementation
        mVolleyImageLoaderImageView = (ImageView) findViewById(R.id.volley_imageloader_view);
        mImageLoader = new ImageLoader(mRequestQueue, new LruBitmapCache(LruBitmapCache.getCacheSize(this)));
        mImageLoader.get(IMAGE_URL, ImageLoader.getImageListener(mVolleyImageLoaderImageView, R.mipmap.ic_launcher, R.drawable.error));

        // for super lazy programmers, you can use NetworkImageView in your View hierarchy that handles
        // request lifecycle for you
        mNetworkImageView = (NetworkImageView) findViewById(R.id.network_image_view);
        mNetworkImageView.setImageUrl(IMAGE_URL, mImageLoader);
        mNetworkImageView.setDefaultImageResId(R.mipmap.ic_launcher);
        mNetworkImageView.setErrorImageResId(R.drawable.error);

    }

    /**
     * AsyncTask that downloads an image in a Worker Thread
     */
    public class HttpURLConnectionImageAsyncTask extends AsyncTask<String, Void, Bitmap>{

        private ImageView mImageView;

        public HttpURLConnectionImageAsyncTask(ImageView imageView){
            this.mImageView = imageView;
        }

        @Override
        protected Bitmap doInBackground(String... strings) {
            HttpURLConnection connection = null;
            try {
                URL imageUrl = new URL(strings[0]);

                connection = (HttpURLConnection) imageUrl.openConnection();
                connection.setReadTimeout(10000);
                connection.setConnectTimeout(15000);
                connection.setRequestMethod("GET");
                connection.setDoInput(true);
                connection.connect();

                if(connection.getResponseCode() ==  HttpURLConnection.HTTP_OK){

                    return BitmapFactory.decodeStream(connection.getInputStream());
                }

                return null;

            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                if(connection != null) connection.disconnect();
            }

            return null;
        }

        @Override
        public void onPostExecute(Bitmap bitmap){
            if (bitmap != null) mImageView.setImageBitmap(bitmap);
        }

    }

    /**
     * AsyncTask that uses HttpURLConnection to make a JSON api request
     * It uses HttpResponseCache for response caching
     */
    public class RandomJokeAsyncTask extends AsyncTask<Void, Void, String>{

        private boolean fetchedFromCache;

        @Override
        protected String doInBackground(Void... voids) {

            fetchedFromCache = false;
            URL randomJoke;
            HttpURLConnection connection = null;
            try {
                randomJoke = new java.net.URL(RANDOM_URL);
                connection = (HttpURLConnection) randomJoke.openConnection();
                connection.setReadTimeout(10000);
                connection.setConnectTimeout(15000);
                connection.setRequestMethod("GET");
                connection.setDoInput(true);

                // check for cache entry if we are on Android 4.0 + device
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    if(HttpResponseCache.getInstalled() == null) enableHttpResponseCache();

                    String cachedResponse = fetchFromHTTPUrlConnectionCache(connection, new URI(RANDOM_URL));
                    if (cachedResponse != null) {
                        fetchedFromCache = true;
                        JSONObject response = new JSONObject(cachedResponse);
                        return response.getJSONObject("value").getString("joke");
                    }
                }

                connection.connect();
                int response = connection.getResponseCode();
                Log.d(TAG, "The response code is: " + response);

                Scanner scanner = new Scanner(connection.getInputStream(), "UTF-8");
                StringBuilder sb = new StringBuilder();
                while (scanner.hasNextLine()){
                    sb.append(scanner.nextLine());
                }

                String content = sb.toString();
                Log.d(TAG, "Service Response => " + content);
                JSONObject jsonResponse = new JSONObject(content);

                // Response example:  { "type": "success", "value": { "id": , "joke": } }

                return jsonResponse.getJSONObject("value").getString("joke");

            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Something went wrong while" +
                        " retrieving json " + e.toString());
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e(TAG, "Something went wrong while" +
                        " parsing json from " + e.toString());
            } catch (URISyntaxException e) {
                e.printStackTrace();
                Log.e(TAG, "Something went wrong while" +
                        " parsing URL " + e.toString());
            } finally {
                if(connection != null) connection.disconnect();
            }

            return null;

        }

        @Override
        public void onPostExecute(String joke){

            if(fetchedFromCache) Toast.makeText(MainActivity.this, "Joke fetched from cache!", Toast.LENGTH_SHORT).show();

            mJokeTextView.setText(joke);

        }

    }

    public void fetchData(View view) {

        switch (view.getId()){
            case R.id.first_approach_btn:
                doAsyncTask();
                break;
            case  R.id.volley_approach_btn:
                doVolley();
                break;
        }

    }

    /**
     * Does the same request that above but using Volley.
     * It uses Volleys Cache. We are forcing the response to be cached as the service returns a no cache header
     */
    private void doVolley() {


        if(!fetchFromVolleyCache()) {

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.GET, RANDOM_URL,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {

                            try {

                                Log.d(TAG, "Service Response => " + response.toString());
                                mJokeTextView.setText(response.getJSONObject("value").getString("joke"));

                            } catch (JSONException e) {
                                e.printStackTrace();
                                Log.d(TAG, "Error parsing response JSON object");
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {

                            if (error.networkResponse != null) {
                                Log.d(TAG, "Error Response code: " + error.networkResponse.statusCode);
                            }
                        }
                    }
            ){
                @Override
                protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                    try {
                        String jsonString = new String(response.data);
                        JSONObject jsonObject = new JSONObject(jsonString);

                        // force response to be cached
                        Map<String, String> headers = response.headers;
                        long cacheExpiration = 24 * 60 * 60 * 1000; // in 24 hours this cache entry expires completely
                        long now = System.currentTimeMillis();
                        Cache.Entry entry = new Cache.Entry();
                        entry.data = response.data;
                        entry.etag = headers.get("ETag");
                        entry.ttl = now + cacheExpiration;
                        entry.serverDate = HttpHeaderParser.parseDateAsEpoch(headers.get("Date"));
                        entry.responseHeaders = headers;

                        return Response.success(jsonObject, entry);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        return Response.error(new VolleyError("Error parsing network response"));
                    }
                }

                @Override
                public Priority getPriority() {
                    return Priority.HIGH;
                }

            };

            // set the default retry policy
            request.setRetryPolicy(
                    new DefaultRetryPolicy());

            // or you can pass in your own retry policy

            /*
            request.setRetryPolicy(new RetryPolicy() {
                @Override
                public int getCurrentTimeout() {
                    return 3000; // 3 seconds
                }

                @Override
                public int getCurrentRetryCount() {
                    return 3; // Attempt to retry at most 3 times
                }

                @Override
                public void retry(VolleyError error) throws VolleyError {
                     // i.e increase retry counter and determine if we reached the maximum retry attempts. If so, throw the error
                }
            });

            */
            mRequestQueue.add(request);
        }

    }

    private void doAsyncTask() {
        new RandomJokeAsyncTask().execute();
    }

    /**
     * Installs the HttpResponseCache
     * Note this will only work on Android 4.0+
     */
    private void enableHttpResponseCache() {
        try {
            long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
            File httpCacheDir = new File(getCacheDir(), "http");
            HttpResponseCache.install(httpCacheDir, httpCacheSize);
        } catch (Exception httpResponseCacheNotAvailable) {
            Log.d(TAG, "HTTP response cache is unavailable.");
        }
    }

    /**
     * Fetches an entry value from the HttpResponseCache cache
     * @param connection connection from which we need the cache
     * @param uri uri to use to get the cache entry
     * @return cache entry value as String
     */
    private String fetchFromHTTPUrlConnectionCache(HttpURLConnection connection, URI uri) {

        try {
            HttpResponseCache responseCache = HttpResponseCache.getInstalled();
            if(responseCache != null){
                CacheResponse cacheResponse = responseCache.get(uri, "GET", connection.getRequestProperties());
                Scanner scanner = new Scanner(cacheResponse.getBody(), "UTF-8");
                StringBuilder sb = new StringBuilder();
                while (scanner.hasNextLine()){
                    sb.append(scanner.nextLine());
                }

                return sb.toString();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;

    }

    /**
     * Fetches entry from Volley cache
     * @return boolean indicating the cache was hit
     */
    private boolean fetchFromVolleyCache(){

        try{

            Cache cache = mRequestQueue.getCache();
            Cache.Entry entry = cache.get(RANDOM_URL);
            if(entry != null) {
                JSONObject cachedResponse = new JSONObject(new String(entry.data, "UTF-8"));
                mJokeTextView.setText(cachedResponse.getJSONObject("value").getString("joke"));
                Toast.makeText(this, "Joke fetched from cache!", Toast.LENGTH_SHORT).show();
                return true;
            }
        }catch (UnsupportedEncodingException | JSONException e){
            e.printStackTrace();
            Log.d(TAG, "Error fetching joke from cache for entry: " + RANDOM_URL);
        }

        return false;
    }

    /**
     * Invalidates both HttpResponseCache and Volley cache
     * @param view
     */
    public void invalidateCache(View view) {

        mRequestQueue.getCache().clear();
        try {
            HttpResponseCache cache = HttpResponseCache.getInstalled();
            if(cache != null) cache.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Toast.makeText(this, "Cache is now empty", Toast.LENGTH_SHORT).show();

    }

    @Override
    protected void onStop(){
        super.onStop();

        // You can cancel all the requests giving a scope object.
        mRequestQueue.cancelAll(this);

        // you can cancel all the requests that meet a certain custom filter
        /*
        mRequestQueue.cancelAll(new RequestQueue.RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                return request instanceof ImageRequest;
            }
        });
        */

    }

}
