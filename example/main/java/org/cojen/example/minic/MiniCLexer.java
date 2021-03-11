// Generated from MiniC.g4 by ANTLR 4.9.1
package org.cojen.example.minic;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class MiniCLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.9.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		IntegerLiteral=1, FloatingPointLiteral=2, StringLiteral=3, BooleanLiteral=4, 
		SEMI=5, WS=6, BLOCK_COMMENT=7, LINE_COMMENT=8, IF_KEYWORD=9, ELSE_KEYWORD=10, 
		WHILE_KEYWORD=11, BREAK_KEYWORD=12, CONTINUE_KEYWORD=13, EXIT_KEYWORD=14, 
		READ_INT_KEYWORD=15, READ_DOUBLE_KEYWORD=16, READ_LINE_KEYWORD=17, TO_STRING_KEYWORD=18, 
		PRINT_KEYWORD=19, PRINTLN_KEYWORD=20, INT_TYPE=21, DOUBLE_TYPE=22, STRING_TYPE=23, 
		BOOL_TYPE=24, MUL=25, DIV=26, PLUS=27, MINUS=28, MOD=29, LT=30, GT=31, 
		LTEQ=32, GTEQ=33, ASSIGN=34, EQ=35, NOTEQ=36, NOT=37, AND=38, OR=39, LPAR=40, 
		RPAR=41, LBRACE=42, RBRACE=43, Identifier=44;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"IntegerLiteral", "FloatingPointLiteral", "StringLiteral", "BooleanLiteral", 
			"SEMI", "DIGIT", "LETTER", "ESC", "UNICODE", "HEX", "WS", "BLOCK_COMMENT", 
			"LINE_COMMENT", "IF_KEYWORD", "ELSE_KEYWORD", "WHILE_KEYWORD", "BREAK_KEYWORD", 
			"CONTINUE_KEYWORD", "EXIT_KEYWORD", "READ_INT_KEYWORD", "READ_DOUBLE_KEYWORD", 
			"READ_LINE_KEYWORD", "TO_STRING_KEYWORD", "PRINT_KEYWORD", "PRINTLN_KEYWORD", 
			"INT_TYPE", "DOUBLE_TYPE", "STRING_TYPE", "BOOL_TYPE", "MUL", "DIV", 
			"PLUS", "MINUS", "MOD", "LT", "GT", "LTEQ", "GTEQ", "ASSIGN", "EQ", "NOTEQ", 
			"NOT", "AND", "OR", "LPAR", "RPAR", "LBRACE", "RBRACE", "Identifier"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, null, "';'", null, null, null, "'if'", "'else'", 
			"'while'", "'break'", "'continue'", "'exit'", "'readInt'", "'readDouble'", 
			"'readLine'", "'toString'", "'print'", "'println'", "'int'", "'double'", 
			"'string'", "'bool'", "'*'", "'/'", "'+'", "'-'", "'%'", "'<'", "'>'", 
			"'<='", "'>='", "'='", "'=='", "'!='", "'!'", "'&&'", "'||'", "'('", 
			"')'", "'{'", "'}'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "IntegerLiteral", "FloatingPointLiteral", "StringLiteral", "BooleanLiteral", 
			"SEMI", "WS", "BLOCK_COMMENT", "LINE_COMMENT", "IF_KEYWORD", "ELSE_KEYWORD", 
			"WHILE_KEYWORD", "BREAK_KEYWORD", "CONTINUE_KEYWORD", "EXIT_KEYWORD", 
			"READ_INT_KEYWORD", "READ_DOUBLE_KEYWORD", "READ_LINE_KEYWORD", "TO_STRING_KEYWORD", 
			"PRINT_KEYWORD", "PRINTLN_KEYWORD", "INT_TYPE", "DOUBLE_TYPE", "STRING_TYPE", 
			"BOOL_TYPE", "MUL", "DIV", "PLUS", "MINUS", "MOD", "LT", "GT", "LTEQ", 
			"GTEQ", "ASSIGN", "EQ", "NOTEQ", "NOT", "AND", "OR", "LPAR", "RPAR", 
			"LBRACE", "RBRACE", "Identifier"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public MiniCLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "MiniC.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2.\u0161\b\1\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\3\2\6\2g\n\2\r\2"+
		"\16\2h\3\3\6\3l\n\3\r\3\16\3m\3\3\3\3\6\3r\n\3\r\3\16\3s\3\4\3\4\3\4\7"+
		"\4y\n\4\f\4\16\4|\13\4\3\4\3\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\5\5"+
		"\u0089\n\5\3\6\3\6\3\7\3\7\3\b\3\b\3\t\3\t\3\t\5\t\u0094\n\t\3\n\3\n\3"+
		"\n\3\n\3\n\3\n\3\13\3\13\3\f\6\f\u009f\n\f\r\f\16\f\u00a0\3\f\3\f\3\r"+
		"\3\r\3\r\3\r\7\r\u00a9\n\r\f\r\16\r\u00ac\13\r\3\r\3\r\3\r\3\r\3\r\3\16"+
		"\3\16\3\16\3\16\7\16\u00b7\n\16\f\16\16\16\u00ba\13\16\3\16\3\16\3\17"+
		"\3\17\3\17\3\20\3\20\3\20\3\20\3\20\3\21\3\21\3\21\3\21\3\21\3\21\3\22"+
		"\3\22\3\22\3\22\3\22\3\22\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23\3\23"+
		"\3\24\3\24\3\24\3\24\3\24\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\26"+
		"\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\27\3\27\3\27\3\27"+
		"\3\27\3\27\3\27\3\27\3\27\3\30\3\30\3\30\3\30\3\30\3\30\3\30\3\30\3\30"+
		"\3\31\3\31\3\31\3\31\3\31\3\31\3\32\3\32\3\32\3\32\3\32\3\32\3\32\3\32"+
		"\3\33\3\33\3\33\3\33\3\34\3\34\3\34\3\34\3\34\3\34\3\34\3\35\3\35\3\35"+
		"\3\35\3\35\3\35\3\35\3\36\3\36\3\36\3\36\3\36\3\37\3\37\3 \3 \3!\3!\3"+
		"\"\3\"\3#\3#\3$\3$\3%\3%\3&\3&\3&\3\'\3\'\3\'\3(\3(\3)\3)\3)\3*\3*\3*"+
		"\3+\3+\3,\3,\3,\3-\3-\3-\3.\3.\3/\3/\3\60\3\60\3\61\3\61\3\62\3\62\5\62"+
		"\u0158\n\62\3\62\3\62\3\62\7\62\u015d\n\62\f\62\16\62\u0160\13\62\3\u00aa"+
		"\2\63\3\3\5\4\7\5\t\6\13\7\r\2\17\2\21\2\23\2\25\2\27\b\31\t\33\n\35\13"+
		"\37\f!\r#\16%\17\'\20)\21+\22-\23/\24\61\25\63\26\65\27\67\309\31;\32"+
		"=\33?\34A\35C\36E\37G I!K\"M#O$Q%S&U\'W(Y)[*]+_,a-c.\3\2\b\4\2$$^^\4\2"+
		"C\\c|\n\2$$\61\61^^ddhhppttvv\5\2\62;CHch\5\2\13\f\16\17\"\"\4\2\f\f\17"+
		"\17\2\u0169\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2"+
		"\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2"+
		"!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3"+
		"\2\2\2\2/\3\2\2\2\2\61\3\2\2\2\2\63\3\2\2\2\2\65\3\2\2\2\2\67\3\2\2\2"+
		"\29\3\2\2\2\2;\3\2\2\2\2=\3\2\2\2\2?\3\2\2\2\2A\3\2\2\2\2C\3\2\2\2\2E"+
		"\3\2\2\2\2G\3\2\2\2\2I\3\2\2\2\2K\3\2\2\2\2M\3\2\2\2\2O\3\2\2\2\2Q\3\2"+
		"\2\2\2S\3\2\2\2\2U\3\2\2\2\2W\3\2\2\2\2Y\3\2\2\2\2[\3\2\2\2\2]\3\2\2\2"+
		"\2_\3\2\2\2\2a\3\2\2\2\2c\3\2\2\2\3f\3\2\2\2\5k\3\2\2\2\7u\3\2\2\2\t\u0088"+
		"\3\2\2\2\13\u008a\3\2\2\2\r\u008c\3\2\2\2\17\u008e\3\2\2\2\21\u0090\3"+
		"\2\2\2\23\u0095\3\2\2\2\25\u009b\3\2\2\2\27\u009e\3\2\2\2\31\u00a4\3\2"+
		"\2\2\33\u00b2\3\2\2\2\35\u00bd\3\2\2\2\37\u00c0\3\2\2\2!\u00c5\3\2\2\2"+
		"#\u00cb\3\2\2\2%\u00d1\3\2\2\2\'\u00da\3\2\2\2)\u00df\3\2\2\2+\u00e7\3"+
		"\2\2\2-\u00f2\3\2\2\2/\u00fb\3\2\2\2\61\u0104\3\2\2\2\63\u010a\3\2\2\2"+
		"\65\u0112\3\2\2\2\67\u0116\3\2\2\29\u011d\3\2\2\2;\u0124\3\2\2\2=\u0129"+
		"\3\2\2\2?\u012b\3\2\2\2A\u012d\3\2\2\2C\u012f\3\2\2\2E\u0131\3\2\2\2G"+
		"\u0133\3\2\2\2I\u0135\3\2\2\2K\u0137\3\2\2\2M\u013a\3\2\2\2O\u013d\3\2"+
		"\2\2Q\u013f\3\2\2\2S\u0142\3\2\2\2U\u0145\3\2\2\2W\u0147\3\2\2\2Y\u014a"+
		"\3\2\2\2[\u014d\3\2\2\2]\u014f\3\2\2\2_\u0151\3\2\2\2a\u0153\3\2\2\2c"+
		"\u0157\3\2\2\2eg\5\r\7\2fe\3\2\2\2gh\3\2\2\2hf\3\2\2\2hi\3\2\2\2i\4\3"+
		"\2\2\2jl\5\r\7\2kj\3\2\2\2lm\3\2\2\2mk\3\2\2\2mn\3\2\2\2no\3\2\2\2oq\7"+
		"\60\2\2pr\5\r\7\2qp\3\2\2\2rs\3\2\2\2sq\3\2\2\2st\3\2\2\2t\6\3\2\2\2u"+
		"z\7$\2\2vy\5\21\t\2wy\n\2\2\2xv\3\2\2\2xw\3\2\2\2y|\3\2\2\2zx\3\2\2\2"+
		"z{\3\2\2\2{}\3\2\2\2|z\3\2\2\2}~\7$\2\2~\b\3\2\2\2\177\u0080\7v\2\2\u0080"+
		"\u0081\7t\2\2\u0081\u0082\7w\2\2\u0082\u0089\7g\2\2\u0083\u0084\7h\2\2"+
		"\u0084\u0085\7c\2\2\u0085\u0086\7n\2\2\u0086\u0087\7u\2\2\u0087\u0089"+
		"\7g\2\2\u0088\177\3\2\2\2\u0088\u0083\3\2\2\2\u0089\n\3\2\2\2\u008a\u008b"+
		"\7=\2\2\u008b\f\3\2\2\2\u008c\u008d\4\62;\2\u008d\16\3\2\2\2\u008e\u008f"+
		"\t\3\2\2\u008f\20\3\2\2\2\u0090\u0093\7^\2\2\u0091\u0094\t\4\2\2\u0092"+
		"\u0094\5\23\n\2\u0093\u0091\3\2\2\2\u0093\u0092\3\2\2\2\u0094\22\3\2\2"+
		"\2\u0095\u0096\7w\2\2\u0096\u0097\5\25\13\2\u0097\u0098\5\25\13\2\u0098"+
		"\u0099\5\25\13\2\u0099\u009a\5\25\13\2\u009a\24\3\2\2\2\u009b\u009c\t"+
		"\5\2\2\u009c\26\3\2\2\2\u009d\u009f\t\6\2\2\u009e\u009d\3\2\2\2\u009f"+
		"\u00a0\3\2\2\2\u00a0\u009e\3\2\2\2\u00a0\u00a1\3\2\2\2\u00a1\u00a2\3\2"+
		"\2\2\u00a2\u00a3\b\f\2\2\u00a3\30\3\2\2\2\u00a4\u00a5\7\61\2\2\u00a5\u00a6"+
		"\7,\2\2\u00a6\u00aa\3\2\2\2\u00a7\u00a9\13\2\2\2\u00a8\u00a7\3\2\2\2\u00a9"+
		"\u00ac\3\2\2\2\u00aa\u00ab\3\2\2\2\u00aa\u00a8\3\2\2\2\u00ab\u00ad\3\2"+
		"\2\2\u00ac\u00aa\3\2\2\2\u00ad\u00ae\7,\2\2\u00ae\u00af\7\61\2\2\u00af"+
		"\u00b0\3\2\2\2\u00b0\u00b1\b\r\2\2\u00b1\32\3\2\2\2\u00b2\u00b3\7\61\2"+
		"\2\u00b3\u00b4\7\61\2\2\u00b4\u00b8\3\2\2\2\u00b5\u00b7\n\7\2\2\u00b6"+
		"\u00b5\3\2\2\2\u00b7\u00ba\3\2\2\2\u00b8\u00b6\3\2\2\2\u00b8\u00b9\3\2"+
		"\2\2\u00b9\u00bb\3\2\2\2\u00ba\u00b8\3\2\2\2\u00bb\u00bc\b\16\2\2\u00bc"+
		"\34\3\2\2\2\u00bd\u00be\7k\2\2\u00be\u00bf\7h\2\2\u00bf\36\3\2\2\2\u00c0"+
		"\u00c1\7g\2\2\u00c1\u00c2\7n\2\2\u00c2\u00c3\7u\2\2\u00c3\u00c4\7g\2\2"+
		"\u00c4 \3\2\2\2\u00c5\u00c6\7y\2\2\u00c6\u00c7\7j\2\2\u00c7\u00c8\7k\2"+
		"\2\u00c8\u00c9\7n\2\2\u00c9\u00ca\7g\2\2\u00ca\"\3\2\2\2\u00cb\u00cc\7"+
		"d\2\2\u00cc\u00cd\7t\2\2\u00cd\u00ce\7g\2\2\u00ce\u00cf\7c\2\2\u00cf\u00d0"+
		"\7m\2\2\u00d0$\3\2\2\2\u00d1\u00d2\7e\2\2\u00d2\u00d3\7q\2\2\u00d3\u00d4"+
		"\7p\2\2\u00d4\u00d5\7v\2\2\u00d5\u00d6\7k\2\2\u00d6\u00d7\7p\2\2\u00d7"+
		"\u00d8\7w\2\2\u00d8\u00d9\7g\2\2\u00d9&\3\2\2\2\u00da\u00db\7g\2\2\u00db"+
		"\u00dc\7z\2\2\u00dc\u00dd\7k\2\2\u00dd\u00de\7v\2\2\u00de(\3\2\2\2\u00df"+
		"\u00e0\7t\2\2\u00e0\u00e1\7g\2\2\u00e1\u00e2\7c\2\2\u00e2\u00e3\7f\2\2"+
		"\u00e3\u00e4\7K\2\2\u00e4\u00e5\7p\2\2\u00e5\u00e6\7v\2\2\u00e6*\3\2\2"+
		"\2\u00e7\u00e8\7t\2\2\u00e8\u00e9\7g\2\2\u00e9\u00ea\7c\2\2\u00ea\u00eb"+
		"\7f\2\2\u00eb\u00ec\7F\2\2\u00ec\u00ed\7q\2\2\u00ed\u00ee\7w\2\2\u00ee"+
		"\u00ef\7d\2\2\u00ef\u00f0\7n\2\2\u00f0\u00f1\7g\2\2\u00f1,\3\2\2\2\u00f2"+
		"\u00f3\7t\2\2\u00f3\u00f4\7g\2\2\u00f4\u00f5\7c\2\2\u00f5\u00f6\7f\2\2"+
		"\u00f6\u00f7\7N\2\2\u00f7\u00f8\7k\2\2\u00f8\u00f9\7p\2\2\u00f9\u00fa"+
		"\7g\2\2\u00fa.\3\2\2\2\u00fb\u00fc\7v\2\2\u00fc\u00fd\7q\2\2\u00fd\u00fe"+
		"\7U\2\2\u00fe\u00ff\7v\2\2\u00ff\u0100\7t\2\2\u0100\u0101\7k\2\2\u0101"+
		"\u0102\7p\2\2\u0102\u0103\7i\2\2\u0103\60\3\2\2\2\u0104\u0105\7r\2\2\u0105"+
		"\u0106\7t\2\2\u0106\u0107\7k\2\2\u0107\u0108\7p\2\2\u0108\u0109\7v\2\2"+
		"\u0109\62\3\2\2\2\u010a\u010b\7r\2\2\u010b\u010c\7t\2\2\u010c\u010d\7"+
		"k\2\2\u010d\u010e\7p\2\2\u010e\u010f\7v\2\2\u010f\u0110\7n\2\2\u0110\u0111"+
		"\7p\2\2\u0111\64\3\2\2\2\u0112\u0113\7k\2\2\u0113\u0114\7p\2\2\u0114\u0115"+
		"\7v\2\2\u0115\66\3\2\2\2\u0116\u0117\7f\2\2\u0117\u0118\7q\2\2\u0118\u0119"+
		"\7w\2\2\u0119\u011a\7d\2\2\u011a\u011b\7n\2\2\u011b\u011c\7g\2\2\u011c"+
		"8\3\2\2\2\u011d\u011e\7u\2\2\u011e\u011f\7v\2\2\u011f\u0120\7t\2\2\u0120"+
		"\u0121\7k\2\2\u0121\u0122\7p\2\2\u0122\u0123\7i\2\2\u0123:\3\2\2\2\u0124"+
		"\u0125\7d\2\2\u0125\u0126\7q\2\2\u0126\u0127\7q\2\2\u0127\u0128\7n\2\2"+
		"\u0128<\3\2\2\2\u0129\u012a\7,\2\2\u012a>\3\2\2\2\u012b\u012c\7\61\2\2"+
		"\u012c@\3\2\2\2\u012d\u012e\7-\2\2\u012eB\3\2\2\2\u012f\u0130\7/\2\2\u0130"+
		"D\3\2\2\2\u0131\u0132\7\'\2\2\u0132F\3\2\2\2\u0133\u0134\7>\2\2\u0134"+
		"H\3\2\2\2\u0135\u0136\7@\2\2\u0136J\3\2\2\2\u0137\u0138\7>\2\2\u0138\u0139"+
		"\7?\2\2\u0139L\3\2\2\2\u013a\u013b\7@\2\2\u013b\u013c\7?\2\2\u013cN\3"+
		"\2\2\2\u013d\u013e\7?\2\2\u013eP\3\2\2\2\u013f\u0140\7?\2\2\u0140\u0141"+
		"\7?\2\2\u0141R\3\2\2\2\u0142\u0143\7#\2\2\u0143\u0144\7?\2\2\u0144T\3"+
		"\2\2\2\u0145\u0146\7#\2\2\u0146V\3\2\2\2\u0147\u0148\7(\2\2\u0148\u0149"+
		"\7(\2\2\u0149X\3\2\2\2\u014a\u014b\7~\2\2\u014b\u014c\7~\2\2\u014cZ\3"+
		"\2\2\2\u014d\u014e\7*\2\2\u014e\\\3\2\2\2\u014f\u0150\7+\2\2\u0150^\3"+
		"\2\2\2\u0151\u0152\7}\2\2\u0152`\3\2\2\2\u0153\u0154\7\177\2\2\u0154b"+
		"\3\2\2\2\u0155\u0158\5\17\b\2\u0156\u0158\7a\2\2\u0157\u0155\3\2\2\2\u0157"+
		"\u0156\3\2\2\2\u0158\u015e\3\2\2\2\u0159\u015d\5\17\b\2\u015a\u015d\5"+
		"\r\7\2\u015b\u015d\7a\2\2\u015c\u0159\3\2\2\2\u015c\u015a\3\2\2\2\u015c"+
		"\u015b\3\2\2\2\u015d\u0160\3\2\2\2\u015e\u015c\3\2\2\2\u015e\u015f\3\2"+
		"\2\2\u015fd\3\2\2\2\u0160\u015e\3\2\2\2\20\2hmsxz\u0088\u0093\u00a0\u00aa"+
		"\u00b8\u0157\u015c\u015e\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}