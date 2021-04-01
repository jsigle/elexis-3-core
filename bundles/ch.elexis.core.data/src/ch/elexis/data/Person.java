/*******************************************************************************
 * Copyright (c) 2005-2009, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 * 
 *******************************************************************************/

package ch.elexis.data;



import java.util.regex.Pattern;

import ch.elexis.core.constants.StringConstants;
import ch.elexis.core.model.ICodeElement;
import ch.elexis.core.types.Gender;
import ch.rgw.tools.JdbcLink;
import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;

/**
 * Eine Person ist ein Kontakt mit zusätzlich Namen, Geburtsdatum und Geschlecht.
 * 
 * @author gerry
 * 
 */
public class Person extends Kontakt {
	// If you add new fields, please be sure to update KontakteView.java tidySelectedAddressesAction
	// (and, most probably, other places)
	public static final String TITLE = "Titel"; //$NON-NLS-1$
	public static final String FLD_TITLE_SUFFIX = "TitelSuffix"; //$NON-NLS-1$
	public static final String MOBILE = "Natel"; //$NON-NLS-1$
	public static final String SEX = "Geschlecht"; //$NON-NLS-1$
	public static final String BIRTHDATE = "Geburtsdatum"; //$NON-NLS-1$
	public static final String FIRSTNAME = "Vorname"; //$NON-NLS-1$
	public static final String NAME = "Name"; //$NON-NLS-1$
	public static final String MALE = "m"; //$NON-NLS-1$
	public static final String FEMALE = "w"; //$NON-NLS-1$
	
	static {
		addMapping(Kontakt.TABLENAME, NAME + "=" + Kontakt.FLD_NAME1, FIRSTNAME + "="
			+ Kontakt.FLD_NAME2, "Zusatz 		=" + Kontakt.FLD_NAME3,
			BIRTHDATE + "=	S:D:Geburtsdatum", SEX, MOBILE + "=NatelNr", //$NON-NLS-1$ //$NON-NLS-2$
			Kontakt.FLD_IS_PERSON, TITLE, FLD_TITLE_SUFFIX);
	}
	
	public String getName(){
		return checkNull(get(NAME));
	}
	
	public String getVorname(){
		return checkNull(get(FIRSTNAME));
	}
	
	public String getGeburtsdatum(){
		return checkNull(get(BIRTHDATE));
	}
	
	public String getGeschlecht(){
		return checkNull(get(SEX));
	}
	
	public Gender getGender() {
		return Gender.fromValue(getGeschlecht().toUpperCase());
	}
	
	public String getNatel(){
		return get(MOBILE);
	}
	
	public boolean isValid(){
		return super.isValid();
	}
	
	/** Eine Person mit gegebener Id aus der Datenbank einlesen */
	public static Person load(String id){
		Person ret = new Person(id);
		return ret;
	}
	
	protected Person(String id){
		super(id);
	}
	
	public Person(){
		// System.out.println("Person");
	}
	
	/** Eine neue Person erstellen */
	public Person(String Name, String Vorname, String Geburtsdatum, String s){
		create(null);
		// String[] vals=new String[]{Name,Vorname,new
		// TimeTool(Geburtsdatum).toString(TimeTool.DATE_COMPACT),s};
		String[] vals = new String[] {
			Name, Vorname, Geburtsdatum, s
		};
		String[] fields = new String[] {
			NAME, FIRSTNAME, BIRTHDATE, SEX
		};
		set(fields, vals);
	}
	
	/**
	 * This constructor is more critical than the previous one
	 * 
	 * @param name
	 *            will be checked for non-alphabetic characters and may not be empty
	 * @param vorname
	 *            will be checked for non alphabetic characters but may be empty
	 * @param gebDat
	 *            will be checked for unplausible values but may be null
	 * @param s
	 *            will be checked for undefined values and may not be empty
	 * @throws PersonDataException
	 */
	public Person(String name, String vorname, TimeTool gebDat, String s)
		throws PersonDataException{
		name = name.trim();
		vorname = vorname.trim();
		if ((StringTool.isNothing(name)) || (!name.matches("[" + StringTool.wordChars + "\\s-]+"))) { //$NON-NLS-1$ //$NON-NLS-2$
			throw new PersonDataException(PersonDataException.CAUSE.LASTNAME);
		}
		if ((!StringTool.isNothing(vorname))
			&& (!vorname.matches("[" + StringTool.wordChars + "\\s-]+"))) { //$NON-NLS-1$ //$NON-NLS-2$
			throw new PersonDataException(PersonDataException.CAUSE.FIRSTNAME);
		}
		String dat = StringTool.leer;
		if (gebDat != null) {
			TimeTool now = new TimeTool();
			int myYear = now.get(TimeTool.YEAR);
			int oYear = gebDat.get(TimeTool.YEAR);
			if (oYear > myYear || oYear < myYear - 120) {
				throw new PersonDataException(PersonDataException.CAUSE.BIRTHDATE);
			}
			dat = gebDat.toString(TimeTool.DATE_COMPACT);
		}
		if (!s.equalsIgnoreCase(Person.MALE) && !s.equalsIgnoreCase(Person.FEMALE)) {
			throw new PersonDataException(PersonDataException.CAUSE.SEX);
		}
		create(null);
		String[] fields = new String[] {
			NAME, FIRSTNAME, BIRTHDATE, SEX
		};
		String[] vals = new String[] {
			name, vorname, dat, s
		};
		set(fields, vals);
	}
	
	/**
	 * Return a short or long label for this Person
	 * 
	 * @return a label describing this Person
	 */
	public String getLabel(boolean shortLabel){
		StringBuilder sb = new StringBuilder();
		
		if (shortLabel) {
			sb.append(getVorname()).append(StringTool.space).append(getName());
			return sb.toString();
		} else {
			return getPersonalia();
		}
		
	}
	
	/**
	 * Initialen holen
	 * 
	 * @param num
	 *            Auf wieviele Stellen der Name gekürzt werden soll
	 */
	public String getInitials(int num){
		StringBuilder ret = new StringBuilder();
		String name = getName();
		String vorname = getVorname();
		String sex = getGeschlecht();
		String geb = getGeburtsdatum();
		if (geb.length() > 7) {
			geb = geb.substring(6);
		}
		ret.append((name.length() > num - 1) ? name.substring(0, num) : name).append(".");
		ret.append((vorname.length() > num - 1) ? vorname.substring(0, num) : vorname).append(".(")
			.append(sex).append("), ").append(geb);
		return ret.toString();
	}
	
	/*
	 * Einen String mit den Personalien holen.
	 *
	 * Stock Elexis Version:
	 * Max Mustermann (m), 01.02.1934, Dipl. biol.
	 * 
	 * Ein allfälliger Titel wie Dr. med. kommt nach Name und Vorname, damit die Suche bei der
	 * Patientsicht nach Namen und Person funktioniert
	 */
	
	//20210401js: added support for different formats to getPersonalia()
	//while maintaining compatibility with the original version
	//and leaving it's format as default

	//Regarding the format string:
	//
	//A section begins with the beginning of the format string or with |
	//To keep it simple, a section ends just before the next section or at the end of the format string,
	//i.e. there no characters can reside between sections.
	//
	//Possible replaceable content identifiers within a section are:
	//[NAME] or [FIRSTNAME] or [SEX] or [BIRTHDATE] or [AGE] or [KUERZEL]
	//To keep it simple, we only support one occurence of each replaceable content identifier.
	//
	//The algorithm works as follows:
	//
	//First pass:
	//For each of the possible replaceable content identifiers
	//(controlled by passed flags withAge and withKuerzel for these two)  
	//	check whether it is contained in the format string,
	//		if yes
	//			then if suitable content is available for the current patient,
	//				then this replaceable content identifier is replaced by the data from the patient.
	//
	//Second pass:
	//For each of the possible replaceable content identifiers,
	//	check whether it is STILL contained in the format string,
	//		if yes,
	//			then identify the first and last character of the respective section,
	//				 and delete this section from the string
	//
	//Finally, return the result.
	//
	
	public static String[] personaliaTemplates = {
	//Stock Elexis 3.x format:
	//Mustermann Max (m), 01.02.1934, Dipl. biol.
	//Mustermann Max (m), 01.02.1934 (88), Dipl. biol.
	//Mustermann Max (m), 01.02.1934 Dipl. biol. - [496]
	//Mustermann Max (m), 01.02.1934 (88), Dipl. biol. - [496]
	"[NAME]| [FIRSTNAME]| ([SEX])|, [BIRTHDATE]|, ([AGE])|, [TITLE] - [[KUERZEL]]",

	//Mustermann Max  (m)  01.02.1934  Dipl. biol.
	//Mustermann Max  (m)  01.02.1934  (88)  Dipl. biol.
	//Mustermann Max  (m)  01.02.1934  Dipl. biol.  [496]
	//Mustermann Max  (m)  01.02.1934  (88)  Dipl. biol.  [496]
	"[NAME]| [FIRSTNAME]|  ([SEX])|  [BIRTHDATE]| ([AGE])|  [TITLE]|  [[KUERZEL]]", 

	//Mustermann Max  m  01.02.1934  Dipl. biol.
	//Mustermann Max  m  01.02.1934  (88)  Dipl. biol.
	//Mustermann Max  m  01.02.1934  Dipl. biol.  [496]
	//Mustermann Max  m  01.02.1934  (88)  Dipl. biol.  [496]
	"[NAME]| [FIRSTNAME]|  [SEX]|  [BIRTHDATE]|  ([AGE])|  [TITLE]|  [[KUERZEL]]", 

	//Mustermann Max  m  01.02.1934  Dipl. biol.
	//Mustermann Max  m  01.02.1934  88  Dipl. biol.
	//Mustermann Max  m  01.02.1934  Dipl. biol.  [496]
	//Mustermann Max  m  01.02.1934  88  Dipl. biol.  [496]
	"[NAME]| [FIRSTNAME]|  [SEX]|  [BIRTHDATE]|  [AGE]|  [TITLE]|  [[KUERZEL]]", 

	//Mustermann Max  (88)  01.02.1934  m  Dipl. biol.
	//Mustermann Max  (88)  01.02.1934  m  Dipl. biol.
	//Mustermann Max  01.02.1934  m  Dipl. biol.  [496]
	//Mustermann Max  (88)  01.02.1934  m  Dipl. biol.  [496]
	"[NAME]| [FIRSTNAME]|  ([AGE])|  [BIRTHDATE]|  [SEX]|  [TITLE]|  [[KUERZEL]]", 

	//Mustermann Max  88  01.02.1934  m  Dipl. biol.
	//Mustermann Max  88  01.02.1934  m  Dipl. biol.
	//Mustermann Max  01.02.1934  m  Dipl. biol.  [496]
	//Mustermann Max  88  01.02.1934  m  Dipl. biol.  [496]
	"[NAME]| [FIRSTNAME]|  [AGE]|  [BIRTHDATE]|  [SEX]|  [TITLE]|  [[KUERZEL]]"
	};

	public static int personaliaDefaultTemplate = 0;
	boolean personaliaDefaultWithAge = false;
	boolean personaliaDefaultWithKuerzel = false;

	String personaliaRemoveUnreplacedSections(String a, String sectionIdentifier) {
		if ( StringTool.isNothing(a) || StringTool.isNothing(sectionIdentifier) ) return a; 
		int posFound = a.indexOf(sectionIdentifier);
		if ( posFound < 0 ) return a; 
		
		int posBefore = posFound-1;
		while ( (posBefore >= 0) && (a.charAt(posBefore) != '|') ) posBefore--; 
		int posAfter = posFound + sectionIdentifier.length();
		while ( (posAfter < a.length() ) && (a.charAt(posAfter) != '|') ) posAfter++;
		
		String b = new String();
		
		if (posBefore > -1) b = a.substring(0,posBefore);
		if (posAfter < a.length() ) b = b+a.substring(posAfter);
		return b;
	}
	
	public String getPersonalia() {
		return getPersonalia(personaliaTemplates[personaliaDefaultTemplate],
				personaliaDefaultWithAge, personaliaDefaultWithKuerzel);
	}
	
	public String getPersonalia(String personaliaTemplate, boolean withAge, boolean withKuerzel) {
		//start with a copy of the supplied format string
		String ret = new String(personaliaTemplate);
		
		//obtain personalia data	
		String[] fields = new String[] {
			NAME, FIRSTNAME, BIRTHDATE, SEX, TITLE,
		};
		String[] vals = new String[fields.length];
		get(fields, vals);													

		//Trim these fields. Otherwise, I've seen at least Title to return " ",
		//i.e. a space - that would actually end up in the personalia String,
		//together with its preceeding separation characters :-(
		for (int i = 0; i<vals.length; i++) {
			vals[i] = vals[i].trim();
		}
		
		//First Pass:
		//fill in available data into format string, subject to availability and control flags
		//Please note:
		//YES, I certainly CAN, but NO, I don't WANT to put this into a for loop based upon the fields array.
		//I prefer to see and understand with a single glance what's happening.
		//Thank you.
		ret = ret.replaceAll(Pattern.quote("[NAME]"),vals[0]);
		ret = ret.replaceAll(Pattern.quote("[FIRSTNAME]"),vals[1]);
		ret = ret.replaceAll(Pattern.quote("[BIRTHDATE]"),vals[2]);
		ret = ret.replaceAll(Pattern.quote("[SEX]"),vals[3]);
		ret = ret.replaceAll(Pattern.quote("[TITLE]"),vals[4]);
		if (withAge) ret = ret.replaceAll(Pattern.quote("[AGE]"),"123");
		if (withKuerzel) ret = ret.replaceAll(Pattern.quote("[KUERZEL]"),"AB");

		/*
		//Second Pass:
		//Scan for sections with identifiers that have NOT been replaced yet.
		//If found, delete them.
		//I'm implementing this by non-greedy pattern matching,
		//but to keep the search string requirements simple for users,
		//this needs a few preparations.
		//Duplicate all | strings:
		System.out.println("Before: "+ret);
		ret = ret.replaceAll(Pattern.quote("|"), "||");
		System.out.println("After:  "+ret);
		//Add extra | at the start and at the end:
		ret = "|"+ret+"|";
		//Do non greedy search for remaining fields, and replace them by nothing:
		System.out.println("Before: "+ret);
		//In java regex logic (like in many others), square brackets designate character classes,
		//and therefore must come in pairs.
		//BUT to escape square brackets, funnily enough however, they must be preceeded by TWO \\ and not ONE \
		//BUT even more funnily, only the FIRST = opening square bracket must (and may) be escaped -
		//after that, the other one is NOT inside a regex class, so it's interpreted as normal square bracket anyway,
		//and trying to escape it would only destroy the match.
		//WELL. It's not a bug. It's not a feature of MS-DOS or MS Windows either - but far beyond: it's Java. 
		//And ONCE AGAIN, hours lost to use a Java built in solution, instead of programming a simple one myself.
		//ret = ret.replaceAll("|.*?[.*?].*?|","");		//Does not work...
		//ret = ret.replaceAll("|.*?\[.*?\].*?|","");	//Is not even accepted by Eclipse...	
		//ret = ret.replaceAll("|.*?\[.*?].*?|","");	//Is not even accepted by Eclipse...	
		//ret = ret.replaceAll("|.*?\\[.*?\\].*?|","");	//Does not work either...
		//ret = ret.replaceAll("|.*?\\[.*?].*?|","");	//Should be correct, STILL does not work... (works in regex simulator, though...)
		//Well, we've got to escape the | as well, or it means "or", so...
		//ret = ret.replaceAll("\|.*?\\[.*?].*?\|","");		//Not accepted by Eclipse...	
		//ret = ret.replaceAll("\\|.*?\\[.*?].*?\\|","");	//Doesn't work as expected (replaces almost all = non-greedy ? ignored :-(
		ret = ret.replaceAll("\\|.*?\\[.+?].*?\\|","");	//Doesn't work as expected (replaces almost all = non-greedy ? ignored :-(
		System.out.println("After:  "+ret);
		
		//Well. - Several hours later, I give up on this new attemt of
		//"quickly using Java regex replacements, even though I know in advance
		//that it will be be extra difficult to read, and most probably never work as expected anyway."
		//Which, by the way, are some of the core differences between Java and Perl... or Pascal... or Assembler... (sic!) :-)
		 */
		
		//Now: Do it myself instead. *That* will be:
		//Simple, comprehensible, doing what it's meant to,
		//and all of that much faster than anyone can merely read the documentation of Java regexes,
		//let alone a couple of forum contributions by fellow users still suffering...      
		ret = personaliaRemoveUnreplacedSections(ret,"[NAME]");
		ret = personaliaRemoveUnreplacedSections(ret,"[FIRSTNAME]");
		ret = personaliaRemoveUnreplacedSections(ret,"[BIRTHDATE]");
		ret = personaliaRemoveUnreplacedSections(ret,"[SEX]");
		ret = personaliaRemoveUnreplacedSections(ret,"[TITLE]");
		ret = personaliaRemoveUnreplacedSections(ret,"[AGE]");
		ret = personaliaRemoveUnreplacedSections(ret,"[KUERZEL]");
		//So funny. After a few minutes, the self made version works like a charm. :-)
		
		//Replace remaining | characters by nothing;
		//these are left over from sections whose content identifier was replaced in pass 1.
		ret = ret.replaceAll(Pattern.quote("|"),"");
		return ret;
	}
	
	@Override
	protected String getConstraint(){
		return new StringBuilder(Kontakt.FLD_IS_PERSON).append(StringTool.equals)
			.append(JdbcLink.wrap(StringConstants.ONE)).toString();
	}
	
	@Override
	protected void setConstraint(){
		set(Kontakt.FLD_IS_PERSON, StringConstants.ONE);
	}
	
	/**
	 * Statistik für ein bestimmtes Objekt führen
	 * 
	 * @param ice
	 */
	public void countItem(ICodeElement ice){
		statForItem(ice);
	}
	
	@SuppressWarnings("serial")
	public static class PersonDataException extends Exception {
		enum CAUSE {
			LASTNAME, FIRSTNAME, BIRTHDATE, SEX
		}
		
		static final String[] causes = new String[] {
			NAME, FIRSTNAME, BIRTHDATE, "Geschlecht (m oder w)"}; //$NON-NLS-1$
		
		public CAUSE cause;
		
		PersonDataException(CAUSE cause){
			super(causes[cause.ordinal()]);
			this.cause = cause;
		}
	}
	
}
