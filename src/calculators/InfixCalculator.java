package calculators;

import static java.lang.Character.isDigit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

import stacks.ArrayStack;

public class InfixCalculator
{
	public static final Character[] operators = new Character[] {'+' , '-' , '*' , '/'};
	public final int scale;
	
	public InfixCalculator(int scale)
	{
		this.scale = scale;
	}
	
	@SuppressWarnings("unused")
	public InfixCalculator()
	{
		this.scale = 4;
	}
	
	private static int priorityOf(char operator)
	{
		// maybe use properties file at some time?
		return switch (operator)
				{
					case '+' , '-' -> 0;
					case '*' , '/' -> 1;
					default -> throw new IllegalArgumentException(
							operator + " is not a recognised operator among " + Arrays.toString(operators));
				};
	}
	
	/**
	 * performs a primary syntax check around the expression
	 * return nothing; throw exceptions for any abnormalities
	 */
	private static void primaryCheck(String expression)
	{
		if (expression == null) throw new NullPointerException();
		
		expression = expression.trim();
		if (expression.length() == 0) throw new IllegalArithmeticExpressionSyntaxException("Empty expression");
		
		// allowed first char: <num> . - + (
		// allowed last char: <num> . = )
		List<Character> permittedStart = List.of('.' , '-' , '+' , '(');
		List<Character> permittedEnd = List.of('.' , '=' , ')');
		
		char firstChar = expression.charAt(0);
		char lastChar = expression.charAt(expression.length() - 1);
		
		if (!(isDigit(firstChar) || permittedStart.contains(firstChar)))
			throw new IllegalArithmeticExpressionSyntaxException("Illegal start of expression: '" + firstChar + "'");
		
		if (!(isDigit(lastChar) || permittedEnd.contains(lastChar)))
			throw new IllegalArithmeticExpressionSyntaxException("Illegal ending of expression: '" + lastChar + "'");
	}
	
	/**
	 * performs a calculation given two operands and an operator.
	 */
	private BigDecimal calc(BigDecimal num1 , char operator , BigDecimal num2)
	{
		return switch (operator)
				{
					case '+' -> num1.add(num2);
					case '-' -> num1.subtract(num2);
					case '*' -> num1.multiply(num2);
					case '/' -> num1.divide(num2 , this.scale , RoundingMode.HALF_UP);
					default -> throw new IllegalArgumentException(
							operator + " is not a recognised operator among " + Arrays.toString(operators));
				};
	}
	
	/**
	 * identifies if a character is an operator by checking if it is in $operators.
	 */
	private boolean isOperator(char c)
	{
		return List.of(operators).contains(c);
	}
	
	/**
	 * scientifically evaluates a given expression.
	 * accepts numerals in decimal and scientific notation.
	 *
	 * @return the computed result as BigDecimal.
	 */
	public /*strictfp*/ BigDecimal compute(String expression)
	{
		// primarily filter illegal syntax at ^$
		primaryCheck(expression);
		
		// expression = expression.trim(); // don't trim, or positions given in the exception will be incorrect
		
		// create two stacks
		ArrayStack<BigDecimal> numStack = new ArrayStack<>(expression.length());
		ArrayStack<Character> operatorStack = new ArrayStack<>(expression.length());
		
		// traverse and scan
		boolean nextIsNum = true;       // used to distinguish + - as unary or binary operator
		boolean equalsFlag = false;     // used to terminate the loop when encounter = sign
		for (int i = 0 ; i < expression.length() && !equalsFlag ; )
		{
			char ch = expression.charAt(i);
			
			if (nextIsNum)
			{
				if (String.valueOf(ch).matches("\\s"))
				{
					i++;
					continue;
				}
				
				// meeting parenthesis while expecting a numeral means grouped calc.
				// evaluate the bracketed content, then push to $numStack.
				// not planning to develop a method to do this as index is moved in the same time
				if (ch == '(')
				{
					int leftBracketIndex = i;
					// take substring until find the corresponding ')'
					// should I allow brackets with no content, ()? not for now
					for (int bracketLv = 1 ; bracketLv > 0 ; )
					{
						i++;
						if (i == expression.length()) throw new IllegalArithmeticExpressionSyntaxException(
								"A right bracket is missing for the left bracket at position " + leftBracketIndex);
						
						ch = expression.charAt(i);
						
						if (ch == '(') bracketLv++;
						else if (ch == ')') bracketLv--;
					}
					
					int rightBracketIndex = i;
					String bracketedExpr = expression.substring(leftBracketIndex + 1 , rightBracketIndex);
					if (bracketedExpr.length() == 0) throw new IllegalArithmeticExpressionSyntaxException(
							"Empty brackets at position " + leftBracketIndex);
					BigDecimal bracketRes = compute(bracketedExpr);
					
					numStack.push(bracketRes);
					nextIsNum = false;
					i++; // $i was at position of ')', now go to next char
					continue;
				}
				
				if (!(isDigit(ch) || ch == '.' || ch == '-' || ch == '+'))
					throw new IllegalArithmeticExpressionSyntaxException("Expecting a numeral at " + i);
				
				StringBuilder numSb = new StringBuilder();
				boolean eFlag = false;
				
				// parse this numeral while moving $i to next appropriate position
				do
				{
					if (ch == 'e') eFlag = true;
					numSb.append(ch); // go to next char
					i++;
					if (i < expression.length()) ch = expression.charAt(i);
					else break;
					
					// if in e mode, allow this "next char" to be a sign
					if (eFlag)
					{
						if (ch == '-' || ch == '+')
						{
							numSb.append(ch);
							i++;
							if (i < expression.length()) ch = expression.charAt(i);
							else break;
						}
						eFlag = false;
					}
				} while (isDigit(ch) || ch == '.' || ch == 'e');
				// syntax problems such as multiple . or e in a numeral will be dealt by BigDecimal constructor
				
				BigDecimal thisNum = new BigDecimal(numSb.toString());
				numStack.push(thisNum);
				nextIsNum = false;
				// no need i++; $i is already at next position
			}
			else if (isOperator(ch))
			{
				if (operatorStack.isEmpty()) operatorStack.push(ch);
				else if (priorityOf(ch) > priorityOf(operatorStack.peek())) operatorStack.push(ch);
				else // finish all operations with higher priority
				{
					finishAllPriorOperations(numStack , operatorStack , ch);
				}
				nextIsNum = true;
				i++;
			}
			else if (String.valueOf(ch).matches("\\s")) i++; // space or tab
			else switch (ch) // other special characters
				{
					case '=' -> {
						// if there are still characters after = sign, give warning
						if (i != expression.length() - 1)
						{
							String RESET = "\u001B[0m";
							String RED_BG = "\u001B[41m";
							String msg = RED_BG + "Warning:" + RESET + " the part after the terminating = sign was ignored";
							System.out.println(msg);
							// originally thrown as an exception, now warning only
						}
						else equalsFlag = true;
					}
					case '(' -> {
						// meeting it while expecting an operator means multiply the content within.
						// try to push * operation.
						if (operatorStack.isEmpty()) operatorStack.push('*');
						else if (priorityOf('*') > priorityOf(operatorStack.peek())) operatorStack.push('*');
						else // finish all operations with higher priority
						{
							finishAllPriorOperations(numStack , operatorStack , '*');
						}
						
						nextIsNum = true; // we then expect a numeral as to evaluate the bracket content.
						// do not i++ or the bracket cannot be detected
					}
					case ')' -> throw new IllegalArithmeticExpressionSyntaxException(
							"A left bracket is missing for the right bracket at position " + i);
					default -> throw new IllegalArithmeticExpressionSyntaxException(
							"Unrecognised symbol '" + ch + "' at position " + i);
				}
		}
		
		// scan finished; calc all nums in the stack from top to bottom
		while (numStack.size() > 1)
		{
			BigDecimal op2 = numStack.pop();
			BigDecimal op1 = numStack.pop();
			char operator = operatorStack.pop();
			
			// note: op2 come in prior to op1
			numStack.push(calc(op1 , operator , op2));
		}
		
		assert numStack.size() == 1 && operatorStack.isEmpty();
		return numStack.pop();
	}
	
	/**
	 * finishes all operations in the stack that are prior to this operation.
	 */
	private void finishAllPriorOperations(ArrayStack<BigDecimal> numStack , ArrayStack<Character> operatorStack ,
	                                      char ch)
	{
		while (operatorStack.size() > 0 && priorityOf(operatorStack.peek()) >= priorityOf(ch))
		{
			BigDecimal op2 = numStack.pop();
			BigDecimal op1 = numStack.pop();
			char operator = operatorStack.pop();
			
			// note: op2 come in prior to op1
			numStack.push(calc(op1 , operator , op2));
		}
		operatorStack.push(ch);
	}
}
