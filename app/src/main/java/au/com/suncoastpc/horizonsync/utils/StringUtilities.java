package au.com.suncoastpc.horizonsync.utils;

import android.annotation.SuppressLint;
import android.os.Build;
import android.text.Html;

import org.apache.commons.httpclient.util.URIUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

/**
 * Static utility-class for simple string-manipulation operations.
 * 
 * @author Adam
 */
public class StringUtilities {
	private static final Random RANDOM_NUMBER_GENERATOR = new Random(System.currentTimeMillis());
	
	//See: 			http://stackoverflow.com/questions/11302361/handling-filename-parameters-with-spaces-via-rfc-5987-results-in-in-filenam
	//See also:  	http://stackoverflow.com/questions/7967079/special-characters-in-content-disposition-filename
	public static String rfc5987_encode(final String s) throws UnsupportedEncodingException {
	    final byte[] s_bytes = s.getBytes("UTF-8");
	    final int len = s_bytes.length;
	    final StringBuilder sb = new StringBuilder(len << 1);
	    final char[] digits = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
	    final byte[] attr_char = {'!','#','$','&','+','-','.','0','1','2','3','4','5','6','7','8','9',           
	    		'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z','^','_','`',                        
	    		'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z','|', '~'};
	    for (int i = 0; i < len; ++i) {
	        final byte b = s_bytes[i];
	        if (Arrays.binarySearch(attr_char, b) >= 0)
	            sb.append((char) b);
	        else {
	            sb.append('%');
	            sb.append(digits[0x0f & (b >>> 4)]);
	            sb.append(digits[b & 0x0f]);
	        }
	    }

	    return sb.toString();
	}

	public static String sanitizedString(String str) {
		return str.toLowerCase().replaceAll("[^a-z]", "");
	}
	
	public static String randomStringWithLengthBetween(int minLength, int maxLength) {
		int delta = maxLength - minLength;
		int length = delta < 1 ? minLength : minLength + RANDOM_NUMBER_GENERATOR.nextInt(delta);	//minLength + (int)(Math.random() * delta);
		
		return randomStringOfLength(length);
	}
	
	public static String randomStringOfLength(int length) {
		StringBuffer buffer = new StringBuffer("");
		while (buffer.length() < length) {
			buffer.append(uuidString());
		}
		
		return buffer.substring(0, length);
	}
	
	public static boolean isEmpty(String test) {
		return test == null || "".equals(test.trim());
	}
	
	private static String uuidString() {
		return UUID.randomUUID().toString().replaceAll("-", "");
	}
	
	public static String getParameter(String name, String queryString) throws UnsupportedEncodingException {
		if (name == null) {
			return null;
		}
		String result = null;
		String[] parts = queryString.split("\\&");
		for (String part : parts) {
			String[] pair = part.split("\\=");
			if (pair.length > 1 && name.equals(pair[0])) {
				result = pair[1];
			}
		}
		
		return URLDecoder.decode(result, "UTF-8").trim();
	}
	
	private static String padUnicodeSequence(String sequence) {
		while (sequence.length() < 4) {
			sequence = "0" + sequence;
		}
		
		return sequence;
	}
	
	public static String escapeUnicode(String text) {
		StringBuffer escaped = new StringBuffer("");
		for (char current : text.toCharArray()) {
			if (current <= 127) {
				escaped.append(current);
			}
			else {
				escaped.append( "\\u" ).append( padUnicodeSequence(Integer.toHexString(current)) );
			}
		}
		
		return escaped.toString();
		//return StringEscapeUtils.escapeJava(text).replace(oldChar, newChar).replace("\\\"", "\"");
	}
	
	@SuppressLint("DefaultLocale")
	public static String saneTag(String searchTerm) {
		if (StringUtilities.isEmpty(searchTerm)) {
			return null;
		}
		if (searchTerm.endsWith(" ")) {
			searchTerm = searchTerm.substring(0, searchTerm.length() - 1) + "-";
		}
		searchTerm = searchTerm.trim().toLowerCase().replaceAll(" ", "").replaceAll("[^a-z\\-]", "");
		
		return searchTerm;
	}
	
	public static String encodeUriComponent(String component) {
		try {
			return URIUtil.encodeWithinQuery(component);
		}
		catch (Exception e) {
			//LOG.warn("Failed to include callback URL component; url=" + component);
		}
		
		return component;
	}
	
	public static boolean nullSafeEquals(String left, String right) {
		if (isEmpty(left) && isEmpty(right)) {
			//both empty, counts as a match
			return true;
		}
		if (! isEmpty(left)) {
			//left is not empty, compare against right
			return left.equals(right);
		}
		
		//left is empty, right is not, no match
		return false;
	}

	public static String unescapeHtmlEntities(String html) {
		if (Build.VERSION.SDK_INT >= 24) {
			return Html.fromHtml(html , Html.FROM_HTML_MODE_LEGACY).toString();

		}
		else {
			return Html.fromHtml(html).toString();
		}
	}
}
