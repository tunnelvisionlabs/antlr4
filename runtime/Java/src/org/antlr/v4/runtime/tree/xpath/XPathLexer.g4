/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

lexer grammar XPathLexer;

tokens { TOKEN_REF, RULE_REF }

/*
path : separator? word (separator word)* EOF ;

separator
	:	'/'  '!'
	|	'//' '!'
	|	'/'
	|	'//'
	;

word:	TOKEN_REF
	|	RULE_REF
	|	STRING
	|	'*'
	;
*/

ANYWHERE : '//' ;
ROOT	 : '/' ;
WILDCARD : '*' ;
BANG	 : '!' ;

ID			:	NameStartChar NameChar*
				{
				String text = getText();
				if ( Character.isUpperCase(text.charAt(0)) ) setType(TOKEN_REF);
				else setType(RULE_REF);
				}
			;

fragment
NameChar    :   [A-Za-z0-9_]
            |   ~[\u0000-\u007F] {Character.isUnicodeIdentifierPart(_input.LA(-1))}?
            ;

fragment
NameStartChar
            :   [A-Za-z]
            |   ~[\u0000-\u007F] {Character.isUnicodeIdentifierStart(_input.LA(-1))}?
            ;

STRING : '\'' .*? '\'' ;

//WS : [ \t\r\n]+ -> skip ;

