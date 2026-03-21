package furniture_system.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * EmailService -- gui email qua Gmail SMTP (jakarta.mail 2.x).
 *
 * Maven dependency (pom.xml):
 *   <dependency>
 *     <groupId>com.sun.mail</groupId>
 *     <artifactId>jakarta.mail</artifactId>
 *     <version>2.0.1</version>
 *   </dependency>
 *
 * Khoi dong app voi JVM flags:
 *   -Dfurniture.gmail.user=yourapp@gmail.com
 *   -Dfurniture.gmail.pass=your_16_char_app_password
 *
 * Tao App Password: Google Account > Security > 2-Step Verification > App passwords
 */
public class EmailService {

    private static final String HOST = "smtp.gmail.com";
    private static final int    PORT = 587;
    private static final String USER =
            System.getProperty("furniture.gmail.user", "yourapp@gmail.com");
    private static final String PASS =
            System.getProperty("furniture.gmail.pass", "app_password");

    public void sendPasswordResetEmail(String to, String username, String otp) {
        Properties props = new Properties();
        props.put("mail.smtp.auth",             "true");
        props.put("mail.smtp.starttls.enable",  "true");
        props.put("mail.smtp.host",             HOST);
        props.put("mail.smtp.port",             String.valueOf(PORT));
        props.put("mail.smtp.connectiontimeout","10000");
        props.put("mail.smtp.timeout",          "10000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USER, PASS);
            }
        });

        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(USER, "Fair Deal Store"));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            msg.setSubject("Fair Deal Store - Password Reset Code", "UTF-8");
            msg.setContent(buildBody(username, otp), "text/html; charset=UTF-8");
            Transport.send(msg);
        } catch (Exception e) {
            throw new RuntimeException("Email send failed to [" + to + "]: " + e.getMessage(), e);
        }
    }

    private String buildBody(String username, String otp) {
        return "<html>"
             + "<body style='font-family:Arial,sans-serif;max-width:460px;margin:auto;'>"
             + "<h2 style='color:#3949ab;'>Fair Deal Furniture</h2>"
             + "<p>Hello <strong>" + username + "</strong>,</p>"
             + "<p>Your password reset code is:</p>"
             + "<div style='font-size:36px;font-weight:bold;letter-spacing:10px;"
             +            "color:#3949ab;text-align:center;padding:20px 0;'>" + otp + "</div>"
             + "<p style='color:#888;font-size:13px;'>"
             +   "This code expires in <strong>15 minutes</strong>.<br>"
             +   "If you did not request this, please ignore this email."
             + "</p>"
             + "<hr style='border:none;border-top:1px solid #eee;margin:24px 0;'>"
             + "<p style='color:#aaa;font-size:11px;'>Fair Deal Store &#169; 2026</p>"
             + "</body></html>";
    }
}