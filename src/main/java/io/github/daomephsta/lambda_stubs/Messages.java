package io.github.daomephsta.lambda_stubs;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS 
{
	public static String generate_lambda,
						 generate_lambda_explicit_parameter_types,
						 generate_lambda_no_parentheses,
						 generate_expression_bodied_lambda,
						 generate_expression_bodied_lambda_explicit_parameter_types,
						 generate_expression_bodied_lambda_no_parentheses;
	
	public static String error_dialog_title,
						 error_applying_proposal,
						 error_computing_proposal;
	
	public static String text_edit_group_generate_lambda;
	
	static
	{
		NLS.initializeMessages("io.github.daomephsta.lambda_stubs.messages", Messages.class);
	}
}
