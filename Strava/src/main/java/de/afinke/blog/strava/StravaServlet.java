package de.afinke.blog.strava;

import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthBearerClientRequest;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAuthzResponse;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthResourceResponse;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class StravaServlet extends HttpServlet {

    public boolean authorize = true;

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (authorize) {
            try {
                // Create the End User Authorization Request by
                OAuthClientRequest request = OAuthClientRequest
                        // providing the Strava authorization endpoint,
                        .authorizationLocation("https://www.strava.com/oauth/authorize")
                        // setting the Client ID of your registered application,
                        .setClientId("3491")
                        // setting response type to code,
                        .setResponseType("code")
                        // setting scope to view_private (optional),
                        .setScope("view_private")
                        // setting the redirect URI back to the servlet.
                        .setRedirectURI("http://localhost:8080/strava-app")
                        .buildQueryMessage();
                authorize = false;
                /* If scope is not public the user is redirected to Strava asking for permission.
                   After the user is redirected to the servlet with filled 'code' request parameter. */
                resp.sendRedirect(request.getLocationUri());
            } catch (OAuthSystemException e) {
                e.printStackTrace();
            }
        } else {
            try {
                // Get the code parameter.
                OAuthAuthzResponse oar = OAuthAuthzResponse.oauthCodeAuthzResponse(req);
                String code = oar.getCode();

                // Create the Application Authorization Request by
                OAuthClientRequest request = OAuthClientRequest
                        // providing the Strava token endpoint,
                        .tokenLocation("https://www.strava.com/oauth/token")
                        // setting grant type to authorization code,
                        .setGrantType(GrantType.AUTHORIZATION_CODE)
                        // setting the Client ID of your registered application,
                        .setClientId("3491")
                        // setting the Client secret of your registered application,
                        .setClientSecret("foobar")
                        // setting the redirect URI back to the servlet,
                        .setRedirectURI("http://localhost:8080/strava-app")
                        // setting the previously requested oauth code.
                        .setCode(code)
                        .buildQueryMessage();

                // Receive your access token.
                OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
                OAuthJSONAccessTokenResponse oAuthResponse = oAuthClient.accessToken(request, OAuthJSONAccessTokenResponse.class);
                String accessToken = oAuthResponse.getAccessToken();

                // Use the access token to query Strava API.
                OAuthClientRequest bearerClientRequest = new OAuthBearerClientRequest("https://www.strava.com/api/v3/athlete/clubs")
                        .setAccessToken(accessToken).buildQueryMessage();

                // Get the response and print the body.
                OAuthResourceResponse resourceResponse = oAuthClient.resource(bearerClientRequest, OAuth.HttpMethod.GET, OAuthResourceResponse.class);
                PrintWriter out = resp.getWriter();
                out.println(resourceResponse.getBody());

                authorize = true;
            } catch (OAuthSystemException e) {
                e.printStackTrace();
            } catch (OAuthProblemException e) {
                e.printStackTrace();
            }
        }
    }
}
