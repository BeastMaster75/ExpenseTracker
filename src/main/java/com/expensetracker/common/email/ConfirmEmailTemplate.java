package com.expensetracker.common.email;

public class ConfirmEmailTemplate {

    public static String confirmEmail(
            String username,
            String confirmationLink
    ) {

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Confirm Your Email</title>
                </head>

                <body style="
                    margin: 0;
                    padding: 0;
                    background-color: #f4f4f4;
                    font-family: Arial, sans-serif;
                ">

                    <div style="
                        max-width: 600px;
                        margin: 40px auto;
                        background: white;
                        padding: 40px;
                        border-radius: 10px;
                        text-align: center;
                    ">

                        <h2 style="color: #333;">
                            Welcome %s!
                        </h2>

                        <p style="color: #555; font-size: 16px;">
                            Thank you for creating an Expense Tracker account.
                        </p>

                        <p style="color: #555; font-size: 16px;">
                            Please confirm your email address by clicking
                            the button below.
                        </p>

                        <a href="%s"
                           style="
                               display: inline-block;
                               margin-top: 20px;
                               padding: 14px 25px;
                               background-color: #4CAF50;
                               color: white;
                               text-decoration: none;
                               border-radius: 6px;
                               font-size: 16px;
                               font-weight: bold;
                           ">
                            Confirm Email
                        </a>

                        <p style="
                            margin-top: 30px;
                            color: #888;
                            font-size: 14px;
                        ">
                            This confirmation link will expire in 15 minutes.
                        </p>

                        <p style="
                            color: #888;
                            font-size: 14px;
                        ">
                            If you did not create this account, you can safely
                            ignore this email.
                        </p>

                        <hr style="
                            margin: 30px 0;
                            border: none;
                            border-top: 1px solid #eee;
                        ">

                        <p style="
                            color: #aaa;
                            font-size: 12px;
                        ">
                            Expense Tracker Team
                        </p>

                    </div>

                </body>
                </html>
                """.formatted(username, confirmationLink);
    }
}