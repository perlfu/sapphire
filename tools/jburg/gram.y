%{
/*
 * (C) Copyright IBM Corp. 2001
 */
#include <stdio.h>
#include "jburg.h"
static char rcsid[] = "$Id$";
/*lint -e616 -e527 -e652 -esym(552,yynerrs) -esym(563,yynewstate,yyerrlab) */
static int yylineno = 0;
static int yylex(void);
%}
%union {
	int n;
	char *string;
	Tree tree;
}
%term TERM
%term START
%term PPERCENT

%token  <string>        ID TEMPLATE CODE
%token  <n>             INT
%type	<string>	nonterm cost
%type   <tree>          tree
%%
spec	: decls PPERCENT rules		{ yylineno = 0; }
	| decls				{ yylineno = 0; }
	;

decls	: /* lambda */
	| decls decl
	;

decl	: TERM  blist '\n'
	| START nonterm '\n'		{
		if (nonterm($2)->number != 1)
			yyerror("redeclaration of the start symbol\n");
		}
	| '\n'
	| error '\n'			{ yyerrok; }
	;

blist	: /* lambda */
	| blist ID '=' INT      	{ term($2, $4); }
	;

rules	: /* lambda */
	| rules nonterm ':' tree TEMPLATE cost '\n'	{ rule($2, $4, "", $6); }
	| rules '\n'
	| rules error '\n'		{ yyerrok; }
	;

nonterm	: ID				{ nonterm($$ = $1); }
	;

tree	: ID                            { $$ = tree($1,  0,  0); }
	| ID '(' tree ')'               { $$ = tree($1, $3,  0); }
	| ID '(' tree ',' tree ')'      { $$ = tree($1, $3, $5); }
	;

cost	: CODE				{ if (*$1 == 0) $$ = xstrdup("0"); }
	;
%%
#include <assert.h>
#include <stdarg.h>
#include <ctype.h>
#include <string.h>
#include <limits.h>

int errcnt = 0;
FILE *infp = NULL;
FILE *outfp = NULL;
static char buf[BUFSIZ], *bp = buf;
static int ppercent = 0;
static int code = 0;

static int 
get(void)
{
    if (*bp == '\0') {
	if (fgets(buf, sizeof buf, infp) == NULL)
	    return EOF;
	bp = buf;
	yylineno++;
	while (buf[0] == '%' && buf[1] == '{' && buf[2] == '\n') {
	    for (;;) {
		if (fgets(buf, sizeof buf, infp) == NULL) {
		    yywarn("unterminated %%{...%%}\n");
		    return EOF;
		}
		yylineno++;
		if (strcmp(buf, "%}\n") == 0)
		    break;
		fputs(buf, outfp);
	    }
	    if (fgets(buf, sizeof buf, infp) == NULL)
		return EOF;
	    yylineno++;
	}
    }
    return *bp++;
}

void 
yyerror(const char *fmt, ...) 
{
    va_list ap;

    va_start(ap, fmt);
    if (yylineno > 0)
	fprintf(stderr, "line %d: ", yylineno);
    vfprintf(stderr, fmt, ap);
    if (fmt[strlen(fmt)-1] != '\n')
	fprintf(stderr, "\n");
    errcnt++;
}


static int 
yylex(void) 
{
    int c;

    if (code) {
	char *p;
	size_t len;
	
	bp += strspn(bp, " \t\f");
	p = strchr(bp, '\n');
	while (p > bp && isspace(p[-1]))
	    p--;
	assert(p >= bp);
	len = p - bp;
	yylval.string = alloc(len + 1);
	strncpy(yylval.string, bp, len);
	yylval.string[len] = '\0';
	bp = p;
	code--;
	return CODE;
    }
    while ((c = get()) != EOF) {
	switch (c) {
	case ' ': case '\f': case '\t':
	    continue;
	case '\n':
	case '(': case ')': case ',':
	case ':': case '=':
	    return c;
	}
	if (c == '%' && *bp == '%') {
	    bp++;
	    return ppercent++ ? 0 : PPERCENT;
	} else if (c == '%' && strncmp(bp, "term", 4) == 0
		   && isspace(bp[4])) {
	    bp += 4;
	    return TERM;
	} else if (c == '%' && strncmp(bp, "start", 5) == 0
		   && isspace(bp[5])) {
	    bp += 5;
	    return START;
	} else if (c == '"') {
	    size_t len;
	    char *p = strchr(bp, '"');
	    
	    if (p == NULL) {
		yyerror("missing \" in assembler template\n");
		p = strchr(bp, '\n');
	    }
	    assert(p);
	    assert(p >= bp);
	    len = p - bp;
	    yylval.string = alloc(len + 1);
	    strncpy(yylval.string, bp, len);
	    yylval.string[len] = '\0';
	    bp = *p == '"' ? p + 1 : p;
	    code++;
	    return TEMPLATE;
	} else if (isdigit(c)) {
	    int n = 0;
	    do {
		int d = c - '0';
		if (n > (INT_MAX - d)/10)
		    yyerror("integer greater than %d\n", INT_MAX);
		else
		    n = 10*n + d;
		c = get();
	    } while (isdigit(c));
	    bp--;
	    yylval.n = n;
	    return INT;
	} else if (isalpha(c)) {
	    char *p = bp - 1;
	    size_t len;
	    
	    while (isalpha(c) || isdigit(c) || c == '_' || c=='$')
		c = get();
	    // Note: get() increments bp.
	    bp--;
	    assert(p <= bp);

	    len = bp - p;
	    yylval.string = alloc(len + 1);
	    strncpy(yylval.string, p, len);
	    yylval.string[len] = 0;
	    return ID;
	} else if (isprint(c))
	    yyerror("illegal character `%c'\n", c);
	else
	    yyerror("illegal character `\\0%o'\n", c);
    }
    return 0;
}

void 
yywarn(const char *fmt, ...) 
{
    va_list ap;

    va_start(ap, fmt);
    if (yylineno > 0)
	fprintf(stderr, "line %d: ", yylineno);
    fprintf(stderr, "warning: ");
    vfprintf(stderr, fmt, ap);
    va_end(ap);
}
