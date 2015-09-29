package com.google.attUsage.connection;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery.TooManyResultsException;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

//Spring Framework controller setting for notification which is used to remind each user the amount of wireless data they used
@Controller
public class NotifyController {
	//entrance point to send text message to the wireless line about their data usage
	@RequestMapping("/datanotify/{telNum}")
	public void notifyUserData(@PathVariable String telNum) throws UnsupportedEncodingException, TooManyResultsException {
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Entity groupEntity = datastore.prepare(new Query("Group")).asSingleEntity();
		if (telNum.equals("all")) {
			Filter filter = new FilterPredicate("telNumber", FilterOperator.NOT_EQUAL, "xxxxxxxxxx");//exclude the owner line
			Query query = new Query("User");
			query.setFilter(filter);
			for (Entity entity : datastore.prepare(query).asIterable()) {
				datanotify(entity, groupEntity);
			}
		} else {
			Filter filter = new FilterPredicate("telNumber", FilterOperator.EQUAL, telNum);
			Query query = new Query("User").setFilter(filter);
			datanotify(datastore.prepare(query).asSingleEntity(), groupEntity);
		}
	}
	
	//entrance point to send text message to the wireless line about their current bill information
	@RequestMapping("/billnotify/{telNum}")
	public void notifyUserBill(@PathVariable String telNum) throws UnsupportedEncodingException {
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Filter filter = new FilterPredicate("status", FilterOperator.EQUAL, "current");
		Query query = new Query("Bill");
		query.setFilter(filter);
		Entity billEntity = datastore.prepare(query).asSingleEntity();
		if (telNum.equals("all")) {
			billnotify(billEntity);
		} else {
			billnotify(telNum, (String)billEntity.getProperty(telNum), (String)billEntity.getProperty("period"));
		}
	}
	
	//Mailout message to Att line about their data usage
	private void billnotify(String telNum, String bill, String period) throws UnsupportedEncodingException {
		String sender = "XXXX@XXX.XX", receiver = telNum + "@txt.att.net";
		String mailBody = "\nYour current At&t bill for " + period + " is: $" + bill;
		mailout(sender, receiver, mailBody);
	}
	
	//notify every line about their bill information
	private void billnotify(Entity entity) throws UnsupportedEncodingException {
		for (Map.Entry<String, Object> entry : entity.getProperties().entrySet()) {
			//exclude owner line and other incorrect fields in Google App Engine DataStore
			if (entry.getKey().equals("XXXXXXXXXX") || entry.getKey().equals("status") || entry.getKey().equals("period") || entry.getKey().equals("totalBill")) {
				continue;
			} else {
				billnotify(entry.getKey(), (String)entry.getValue(), (String)entity.getProperty("period"));
			}
		}
	}
	
	//Mailout message to Att line about their data usage
	private void mailout(String sender, String receiver, String mailBody) throws UnsupportedEncodingException {
		Session session = Session.getDefaultInstance(new Properties(), null);
        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(sender, "At&t Group"));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(receiver, ""));
            msg.setText(mailBody);
            Transport.send(msg);
        } catch (AddressException e) {
            // ...
        } catch (MessagingException e) {
            // ...
        }
	}
	
	//send text message to each Att line via email according to the information stored in Google App Engine DataStore
	private void datanotify(Entity entity, Entity groupEntity) throws UnsupportedEncodingException {
		User user = User.getUserFromEntity(entity);
		String mailBody = "\nYou've used " + user.dataUsage + " GB data, " + user.textUsage + " of unlimited messages.\n";
		mailBody += "Whole group uses: " + String.format("%.2f", (Double)(groupEntity.getProperty("totalUsage"))) + " GB of 40 GB data.\n";
		mailBody += "Next billing cycle: " + String.valueOf((Long)(groupEntity.getProperty("billingCycle"))) + " days after.\n";
		String receiver = user.telNumber + "@txt.att.net", sender = "XXXX@XXX.XX";
		mailout(sender, receiver, mailBody);
	}
}
