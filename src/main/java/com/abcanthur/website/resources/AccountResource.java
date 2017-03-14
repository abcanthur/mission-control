package com.abcanthur.website.resources;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.mindrot.jbcrypt.BCrypt;
import org.postgresql.util.Base64;
import org.postgresql.util.PSQLException;

import com.abcanthur.website.codegen.Tables;
import com.abcanthur.website.codegen.tables.records.SessionsRecord;
import com.abcanthur.website.codegen.tables.records.UsersRecord;

import static com.abcanthur.website.codegen.Tables.*;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.Random;

@Path("/accounts")
public class AccountResource {
	
	@POST
	@Path("/login")
	@Produces(MediaType.TEXT_PLAIN)
	public String login(
			@FormParam("email") Optional<String> email,
			@FormParam("password") Optional<String> password,
			@Context DSLContext database
	) {
		if (!password.isPresent() || !email.isPresent()) {
			throw new WebApplicationException("Login Failed", 401); 
		}
		UsersRecord user = database.selectFrom(USERS)
				.where(USERS.EMAIL.equal(email.get()))
				.fetchOne();
		if (user == null) {
			throw new WebApplicationException("Login Failed", 401);
		}
		if (!BCrypt.checkpw(password.get(), user.getPassword())) {
			throw new WebApplicationException("Login Failed", 401); 
		}
		SessionsRecord session = database.selectFrom(SESSIONS)
				.where(SESSIONS.USER_ID.equal(user.getId()))
				.fetchOne();
		String token = new String();
		if (session == null) {
			boolean tokenUnique = false;
			while (!tokenUnique) {
				String tokenTemp = generateSessionToken();
				SessionsRecord session2 = database.selectFrom(SESSIONS)
						.where(SESSIONS.TOKEN.equal(tokenTemp))
						.fetchOne();
				if (session2 == null) {
					token = tokenTemp;
					tokenUnique = true; 
				}
			}
			database.insertInto(SESSIONS,
					SESSIONS.USER_ID, SESSIONS.TOKEN)
			.values(user.getId(), token);
		} else {
			Long thirtyDays = new Long(30 * 24 * 60 * 60 * 1000);
			Timestamp newTS = new Timestamp(System.currentTimeMillis() + thirtyDays);
			database.update(SESSIONS)
			.set(SESSIONS.EXPIRES_AT, newTS)
			.where(SESSIONS.TOKEN.equal(session.getToken()));
			token = session.getToken();
		}
		return "Login Successful!     Here is your session token : " + token;
	}
	
	@SET
	@Path("/loginupdate")
	@Produces(MediaType.TEXT_PLAIN)
	public String create(
			@FormParam("email") Optional<String> email,
			@FormParam("password") Optional<String> password,
			@Context DSLContext database
	) {
	
	}
	
	@POST
	@Path("/create")
	@Produces(MediaType.TEXT_PLAIN)
	public String create(
			@FormParam("email") Optional<String> email,
			@FormParam("password") Optional<String> password,
			@Context DSLContext database
	) {
		if (!email.isPresent()) throw new WebApplicationException("Please provide an email address", 400);
		if (!password.isPresent()) throw new WebApplicationException("Please provide a password", 400);
		if (password.get().length() < 8) throw new WebApplicationException("Password must be at least 8 characters long", 400);
		if (!isEmailValid(email.get())) throw new WebApplicationException("Please provide a valid email address", 400);
		
		try {
			database.insertInto(USERS, USERS.EMAIL, USERS.PASSWORD)
				.values(email.get(), BCrypt.hashpw(password.get(), BCrypt.gensalt()))
				.execute();
		} catch (DataAccessException e) {
			throw new WebApplicationException("email already in use by another account", 400);
		}
		String createAcctLog = "account created: " + email.get();
		return createAcctLog;
	}
	
	public boolean isEmailValid(String email) {
		if (!email.contains("@")) return false;
		String[] emailNameDom = email.split("@");
		if (emailNameDom.length > 2) return false;
		if (emailNameDom[0].length() < 1) return false;
		if (emailNameDom[1].length() < 1) return false;
		if (!emailNameDom[1].contains(".")) return false;
		String[] emailDom = emailNameDom[1].split("\\.");
		if (emailDom[0].length() < 1) return false;
		if (emailDom[1].length() < 1) return false;
		return true;
	}
	
	public String generateSessionToken() {
		Random ran = new SecureRandom();
		byte [] tokenBytes = new byte[32];
		ran.nextBytes(tokenBytes);
		String token = Base64.encodeBytes(tokenBytes);
//		Base64.Encoder(tokenBytes);
//		Base64.encodeBase64String( tokenBytes );
//		String token = new String(tokenBytes,"utf-8");
		return token;
	}
	
	
}