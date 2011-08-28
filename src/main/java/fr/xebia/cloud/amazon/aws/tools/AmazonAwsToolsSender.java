/*
 * Copyright 2008-2010 Xebia and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.xebia.cloud.amazon.aws.tools;

import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nonnull;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.GetUserRequest;
import com.amazonaws.services.identitymanagement.model.ListSigningCertificatesRequest;
import com.amazonaws.services.identitymanagement.model.NoSuchEntityException;
import com.amazonaws.services.identitymanagement.model.SigningCertificate;
import com.amazonaws.services.identitymanagement.model.User;
import com.amazonaws.services.identitymanagement.model.statusType;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.amazonaws.services.simpleemail.model.SendEmailResult;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;

import fr.xebia.cloud.cloudinit.FreemarkerUtils;

/**
 * Send tools links via email
 * 
 * @author <a href="mailto:ebriand@xebia.fr">Eric Briand</a>
 */
public class AmazonAwsToolsSender {

	public static void main(String[] args) throws Exception {
		try {
			AmazonAwsToolsSender amazonAwsToolsSender = new AmazonAwsToolsSender();
			amazonAwsToolsSender.sendEmails();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected AmazonEC2 ec2;

	protected AmazonIdentityManagement iam;

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected Session mailSession;

	protected Transport mailTransport;
	protected InternetAddress mailFrom;

	protected AmazonSimpleEmailService ses;

	public AmazonAwsToolsSender() {
		try {

			InputStream credentialsAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("AwsCredentials.properties");
			Preconditions.checkNotNull(credentialsAsStream, "File '/AwsCredentials.properties' NOT found in the classpath");
			AWSCredentials awsCredentials = new PropertiesCredentials(credentialsAsStream);
			iam = new AmazonIdentityManagementClient(awsCredentials);

			ses = new AmazonSimpleEmailServiceClient(awsCredentials);

			ec2 = new AmazonEC2Client(awsCredentials);
			ec2.setEndpoint("ec2.eu-west-1.amazonaws.com");

			InputStream smtpPropertiesAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("smtp.properties");
			Preconditions.checkNotNull(smtpPropertiesAsStream, "File '/smtp.properties' NOT found in the classpath");

			final Properties smtpProperties = new Properties();
			smtpProperties.load(smtpPropertiesAsStream);

			mailSession = Session.getInstance(smtpProperties, null);
			mailTransport = mailSession.getTransport();
			if (smtpProperties.containsKey("mail.username")) {
				mailTransport.connect(smtpProperties.getProperty("mail.username"), smtpProperties.getProperty("mail.password"));
			} else {
				mailTransport.connect();
			}
			try {
				mailFrom = new InternetAddress(smtpProperties.getProperty("mail.from"));
			} catch (Exception e) {
				throw new MessagingException("Exception parsing 'mail.from' from 'smtp.properties'", e);
			}

		} catch (Exception e) {
			throw Throwables.propagate(e);
		}
	}

	/**
	 * <p>
	 * Create an Amazon IAM account and send the details by email.
	 * </p>
	 * <p>
	 * Created elements:
	 * </p>
	 * <ul>
	 * <li>password to login to the management console if none exists,</li>
	 * <li>accesskey if none is active,</li>
	 * <li></li>
	 * </ul>
	 * 
	 * @param userName
	 *            valid email used as userName of the created account.
	 */
	public void sendEmail(@Nonnull final String userName) throws Exception {
		Preconditions.checkNotNull(userName, "Given userName can NOT be null");
		logger.debug("Process user {}", userName);

		Map<String, String> templatesParams = Maps.newHashMap();
		templatesParams.put("awsCredentialsHome", "~/.aws");
		templatesParams.put("awsCommandLinesHome", "~/aws-tools");

		User user;

		try {
			user = iam.getUser(new GetUserRequest().withUserName(userName)).getUser();
		} catch (NoSuchEntityException e) {
			logger.debug("User {} does not exist,", userName, e);
			throw e;
		}

		List<BodyPart> attachments = Lists.newArrayList();

		templatesParams.put("credentialsFileName", "aws-credentials.txt");

		// X509 SELF SIGNED CERTIFICATE
		Collection<SigningCertificate> certificates = iam.listSigningCertificates(new ListSigningCertificatesRequest().withUserName(user.getUserName())).getCertificates();
		// filter active certificates
		certificates = Collections2.filter(certificates, new Predicate<SigningCertificate>() {
			@Override
			public boolean apply(SigningCertificate signingCertificate) {
				return statusType.Active.equals(statusType.fromValue(signingCertificate.getStatus()));
			}
		});

		SigningCertificate signingCertificate = Iterables.getFirst(certificates, null);
		templatesParams.put("X509CertificateFileName", "cert-" + signingCertificate.getCertificateId() + ".pem");
		templatesParams.put("X509PrivateKeyFileName", "pk-" + signingCertificate.getCertificateId() + ".pem");

		// email attachment: profile-fragment
		{
			BodyPart profileFragmentBodyPart = new MimeBodyPart();
			profileFragmentBodyPart.setFileName("profile-fragement");
			templatesParams.put("attachedProfileFragmentFileName", profileFragmentBodyPart.getFileName());
			String profileFragment = FreemarkerUtils.generate(templatesParams, "/fr/xebia/cloud/amazon/aws/tools/profile-fragment.fmt");
			profileFragmentBodyPart.setContent(profileFragment, "text/plain");
			attachments.add(profileFragmentBodyPart);
		}

		sendEmail(templatesParams, attachments, userName);
	}

	public void sendEmails() {
		URL emailsToVerifyURL = Thread.currentThread().getContextClassLoader().getResource("users-to-notify.txt");
		Preconditions.checkNotNull(emailsToVerifyURL, "File 'users-to-notify.txt' NOT found in the classpath");
		Collection<String> userNames;
		try {
			userNames = Resources.readLines(emailsToVerifyURL, Charsets.ISO_8859_1);
		} catch (Exception e) {
			throw Throwables.propagate(e);
		}
		for (String userName : userNames) {
			try {
				sendEmail(userName);
			} catch (Exception e) {
				logger.error("Failure to create user '{}'", userName, e);
			}

			// sleep 10 seconds to prevent "Throttling exception"
			try {
				Thread.sleep(10 * 1000);
			} catch (InterruptedException e) {
				throw Throwables.propagate(e);
			}
		}
	}

	/**
	 * 
	 * @param subject
	 * @param htmlBody
	 * @param toAddress
	 * @throws MessagingException
	 */
	public void sendEmail(Map<String, String> templatesParams, List<BodyPart> attachments, String toAddress) throws MessagingException {

		MimeBodyPart htmlAndPlainTextAlternativeBody = new MimeBodyPart();

		// TEXT AND HTML MESSAGE (gmail requires plain text alternative,
		// otherwise, it displays tes 1st plain text attachment in the preview)
		MimeMultipart cover = new MimeMultipart("alternative");
		htmlAndPlainTextAlternativeBody.setContent(cover);
		BodyPart textHtmlBodyPart = new MimeBodyPart();
		String textHtmlBody = FreemarkerUtils.generate(templatesParams, "/fr/xebia/cloud/amazon/aws/tools/amazon-aws-tools-email.html.fmt");
		textHtmlBodyPart.setContent(textHtmlBody, "text/html");
		cover.addBodyPart(textHtmlBodyPart);

		BodyPart textPlainBodyPart = new MimeBodyPart();
		cover.addBodyPart(textPlainBodyPart);
		String textPlainBody = FreemarkerUtils.generate(templatesParams, "/fr/xebia/cloud/amazon/aws/tools/amazon-aws-tools-email.txt.fmt");
		textPlainBodyPart.setContent(textPlainBody, "text/plain");

		MimeMultipart content = new MimeMultipart("related");
		content.addBodyPart(htmlAndPlainTextAlternativeBody);

		// ATTACHMENTS
		for (BodyPart bodyPart : attachments) {
			content.addBodyPart(bodyPart);
		}

		MimeMessage msg = new MimeMessage(mailSession);

		msg.setFrom(mailFrom);
		msg.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(toAddress));
		msg.addRecipient(javax.mail.Message.RecipientType.CC, mailFrom);

		msg.setSubject("Xebia France Amazon EC2 Training Tools");
		msg.setContent(content);

		mailTransport.sendMessage(msg, msg.getAllRecipients());
	}

	/**
	 * Send email with Amazon Simple Email Service.
	 * <p/>
	 * 
	 * Please note that the sender (ie 'from') must be a verified address (see
	 * {@link AmazonSimpleEmailService#verifyEmailAddress(com.amazonaws.services.simpleemail.model.VerifyEmailAddressRequest)}
	 * ).
	 * <p/>
	 * 
	 * Please note that the sender is a CC of the meail to ease support.
	 * <p/>
	 * 
	 * @param subject
	 * @param body
	 * @param from
	 * @param toAddresses
	 */

	public void sendEmail(String subject, String body, String from, String... toAddresses) {

		SendEmailRequest sendEmailRequest = new SendEmailRequest( //
				from, //
				new Destination().withToAddresses(toAddresses).withCcAddresses(from), //
				new Message(new Content(subject), //
						new Body(new Content(body))));
		SendEmailResult sendEmailResult = ses.sendEmail(sendEmailRequest);
		System.out.println(sendEmailResult);
	}

}
