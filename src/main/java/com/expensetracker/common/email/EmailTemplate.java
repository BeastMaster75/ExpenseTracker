package com.expensetracker.common.email;

public class EmailTemplate {

    private EmailTemplate() {
    }

    public static String otpEmail(
            String username,
            int otp,
            String purpose
    ) {

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Verification Code</title>
                </head>

                <body style="
                    margin: 0;
                    padding: 0;
                    background-color: #f4f6f8;
                    font-family: Arial, Helvetica, sans-serif;
                ">

                    <table width="100%%" cellpadding="0" cellspacing="0"
                           style="background-color: #f4f6f8; padding: 40px 0;">

                        <tr>
                            <td align="center">

                                <table width="600" cellpadding="0" cellspacing="0"
                                       style="
                                           max-width: 600px;
                                           width: 90%%;
                                           background-color: #ffffff;
                                           border-radius: 12px;
                                           overflow: hidden;
                                           box-shadow: 0 4px 15px rgba(0,0,0,0.08);
                                       ">

                                    <!-- Header -->
                                    <tr>
                                        <td style="
                                            background-color: #111827;
                                            padding: 28px;
                                            text-align: center;
                                        ">
                                            <h1 style="
                                                margin: 0;
                                                color: #ffffff;
                                                font-size: 24px;
                                            ">
                                                First Project
                                            </h1>
                                        </td>
                                    </tr>

                                    <!-- Content -->
                                    <tr>
                                        <td style="padding: 40px 35px;">

                                            <h2 style="
                                                margin-top: 0;
                                                color: #111827;
                                                font-size: 22px;
                                            ">
                                                Hello %s 👋
                                            </h2>

                                            <p style="
                                                color: #4b5563;
                                                font-size: 15px;
                                                line-height: 1.6;
                                            ">
                                                You requested a verification code for
                                                <strong>%s</strong>.
                                            </p>

                                            <p style="
                                                color: #4b5563;
                                                font-size: 15px;
                                                line-height: 1.6;
                                            ">
                                                Use the verification code below to continue:
                                            </p>

                                            <!-- OTP -->
                                            <div style="
                                                margin: 30px 0;
                                                padding: 20px;
                                                background-color: #f3f4f6;
                                                border-radius: 10px;
                                                text-align: center;
                                                letter-spacing: 8px;
                                                font-size: 32px;
                                                font-weight: bold;
                                                color: #111827;
                                            ">
                                                %s
                                            </div>

                                            <p style="
                                                color: #6b7280;
                                                font-size: 14px;
                                                line-height: 1.6;
                                            ">
                                                This code will expire in
                                                <strong>3 Minutes</strong>.
                                            </p>

                                            <p style="
                                                color: #6b7280;
                                                font-size: 14px;
                                                line-height: 1.6;
                                            ">
                                                If you didn't request this code,
                                                you can safely ignore this email.
                                            </p>

                                        </td>
                                    </tr>

                                    <!-- Footer -->
                                    <tr>
                                        <td style="
                                            padding: 20px 35px;
                                            background-color: #f9fafb;
                                            text-align: center;
                                        ">
                                            <p style="
                                                margin: 0;
                                                color: #9ca3af;
                                                font-size: 12px;
                                            ">
                                                © 2026 First Project. All rights reserved.
                                            </p>
                                        </td>
                                    </tr>

                                </table>

                            </td>
                        </tr>

                    </table>

                </body>
                </html>
                """.formatted(
                username,
                purpose,
                otp
        );
    }
}
