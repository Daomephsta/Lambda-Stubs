package io.github.daomephsta.lambda_stubs;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;

/**
 * Encapsulates and validates options for a lambda
 * @author Daomephsta
 */
public class LambdaSpecification 
{
	/**The functional interface type these options are valid for*/
	public final ITypeBinding lambdaType;
	/**If true the lambda will explicitly specify its parameter types*/
	public final boolean explicitTypes,
	/**If true the lambda will be expression-bodied*/
						  expressionBodied,
	/**If true the lambda will omit the parentheses around its parameter list*/
						  omitParentheses;

	/**
	 * @param lambdaType the functional interface type to validate the specification 
	 * @param explicitTypes specifies if the types of the lambda's parameters will be explicitly specified 
	 * @param expressionBodied specifies if the lambda will be expression-bodied
	 * @param omitParentheses specifies if the lambda will omit the parentheses around its parameter list
	 * @throws JavaModelException if an error occurs when accessing the backing java model of the lambda type
	 */
	public LambdaSpecification(ITypeBinding lambdaType, boolean explicitTypes, boolean expressionBodied, boolean omitParentheses) throws JavaModelException 
	{	
		IMethodBinding functionalInterfaceMethodBinding = lambdaType.getFunctionalInterfaceMethod();
		if (functionalInterfaceMethodBinding == null)
			throw new IllegalArgumentException(lambdaType.getQualifiedName() + " is not a functional interface");
		IMethod functionalInterfaceMethod = (IMethod) functionalInterfaceMethodBinding.getMethodDeclaration().getJavaElement();
		

		if (explicitTypes && omitParentheses)
			throw new IllegalArgumentException("Parentheses cannot be ommitted if parameter types are explicit");
		
		if (lambdaType.getFunctionalInterfaceMethod().getParameterTypes().length != 1 && omitParentheses)
			throw new IllegalArgumentException("Parentheses cannot be ommitted if the lambda has multiple parameters");
		
		if (Signature.SIG_VOID.equals(functionalInterfaceMethod.getReturnType()) && expressionBodied)
			throw new IllegalArgumentException("Lambdas that return void cannot be expression-bodied");
		
		this.lambdaType = lambdaType;
		this.explicitTypes = explicitTypes;
		this.expressionBodied = expressionBodied;
		this.omitParentheses = omitParentheses;
	}

	/**
	 * @param lambdaType the functional interface the specification should be valid for
	 * @return the set of all LambdaSpecifications valid for the given lambda type
	 * @throws JavaModelException if an error occurs when accessing the backing java model of the lambda type 
	 */
	public static Set<LambdaSpecification> computeValidSpecifications(ITypeBinding lambdaType) throws JavaModelException
	{
		IMethodBinding functionalInterfaceMethodBinding = lambdaType.getFunctionalInterfaceMethod();
		if (functionalInterfaceMethodBinding == null)
			throw new IllegalArgumentException(lambdaType.getQualifiedName() + " is not a functional interface");
		IMethod functionalInterfaceMethod = (IMethod) functionalInterfaceMethodBinding.getMethodDeclaration().getJavaElement();
		//3 booleans, with 2 being mutually exclusive, so max 2^2 (4) options
		Set<LambdaSpecification> validOptions = new HashSet<>(4);
		boolean[] bools = {true, false};
		for (boolean explicitTypes : bools)
		{
			for (boolean expressionBodied : bools)
			{	
				//Expression bodies aren't possible for lambdas with a void return type
				if (Signature.SIG_VOID.equals(functionalInterfaceMethod.getReturnType()) && expressionBodied)
					continue;
				for (boolean omitParentheses : bools)
				{
					//Omitting parentheses is only valid if the only parameter has an inferred type
					if (!(lambdaType.getFunctionalInterfaceMethod().getParameterTypes().length == 1 && !explicitTypes) && omitParentheses)
						continue;
					validOptions.add(new LambdaSpecification(lambdaType, explicitTypes, expressionBodied, omitParentheses));
				}
			} 
		}
		return validOptions;
	}
	
	/**
	 * @param ast a factory for creating new AST nodes 
	 * @param importRewriter an AST rewriter to accept any new imports that are required
	 * @return a lambda created from these options 
	 * @throws JavaModelException if an error occurs when accessing the backing java model of the lambda type
	 */
	@SuppressWarnings("unchecked")
	public LambdaExpression create(AST ast, ImportRewrite importRewriter) throws JavaModelException 
	{
		IMethodBinding functionalInterfaceMethodBinding = lambdaType.getFunctionalInterfaceMethod();
		IMethod functionalInterfaceMethod = (IMethod) functionalInterfaceMethodBinding.getMethodDeclaration().getJavaElement();
		LambdaExpression lambdaExpression = ast.newLambdaExpression();
		for (int p = 0; p < functionalInterfaceMethod.getNumberOfParameters(); p++)
		{
			ITypeBinding parameterType = functionalInterfaceMethodBinding.getParameterTypes()[p];
			String parameterName = functionalInterfaceMethod.getParameterNames()[p];
			if (explicitTypes)
			{
				SingleVariableDeclaration parameterDeclaration = ast.newSingleVariableDeclaration();
				parameterDeclaration.setType(importRewriter.addImport(parameterType, ast));
				parameterDeclaration.setName(ast.newSimpleName(parameterName));
				lambdaExpression.parameters().add(parameterDeclaration);
			}
			else
			{
				VariableDeclarationFragment parameterDeclaration = ast.newVariableDeclarationFragment();
				parameterDeclaration.setName(ast.newSimpleName(parameterName));
				lambdaExpression.parameters().add(parameterDeclaration);
			}
		}
		if (!Signature.SIG_VOID.equals(functionalInterfaceMethod.getReturnType()))
		{
			if (expressionBodied)
			{
				Block nullReturn = ast.newBlock();
				ReturnStatement returnStatement = ast.newReturnStatement();
				returnStatement.setExpression(ast.newNullLiteral());
				nullReturn.statements().add(returnStatement);
				lambdaExpression.setBody(nullReturn);
			}
			else
				lambdaExpression.setBody(ast.newNullLiteral());
		}
		if (omitParentheses)
			lambdaExpression.setParentheses(false);
		return lambdaExpression;
	}

	/**
	 * @return a String describing the lambda this specification creates
	 */
	public String describe()
	{
		if (expressionBodied)
		{
			if (explicitTypes)
				return Messages.generate_expression_bodied_lambda_explicit_parameter_types;
			else if (omitParentheses)
				return Messages.generate_expression_bodied_lambda_no_parentheses;
			else
				return Messages.generate_expression_bodied_lambda;
		}
		else
		{
			if (explicitTypes)
				return Messages.generate_lambda_explicit_parameter_types;
			else if (omitParentheses)
				return Messages.generate_lambda_no_parentheses;
			else
				return Messages.generate_lambda;
		}
	}
}
