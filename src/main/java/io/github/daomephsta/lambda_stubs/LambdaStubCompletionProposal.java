package io.github.daomephsta.lambda_stubs;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

class LambdaStubCompletionProposal implements ICompletionProposal 
{
	private final CompilationUnit astRoot;
	private final ASTNode replacementTarget;
	private final LambdaSpecification lambdaSpecification;
	private final String lambdaDescription;
	
	public LambdaStubCompletionProposal(CompilationUnit astRoot, ASTNode replacementTarget, LambdaSpecification lambdaSpecification) 
	{
		this.astRoot = astRoot;
		this.replacementTarget = replacementTarget;
		this.lambdaSpecification = lambdaSpecification;
		this.lambdaDescription = lambdaSpecification.describe();
	}

	@Override
	public void apply(IDocument document) 
	{	
		try 
		{
			ASTRewrite astRewriter = ASTRewrite.create(astRoot.getAST());
			ImportRewrite importRewriter = ImportRewrite.create(astRoot, true);
			TextEditGroup editGroup = new TextEditGroup(Messages.text_edit_group_generate_lambda);
			
			LambdaExpression lambda = lambdaSpecification.create(astRoot.getAST(), importRewriter);
			astRewriter.replace(replacementTarget, lambda, editGroup);
			
			TextEdit importEdits = importRewriter.rewriteImports(null),
					 astEdits = astRewriter.rewriteAST(document, JavaCore.getOptions());
			editGroup.addTextEdit(importEdits);
			
			importEdits.apply(document);
			astEdits.apply(document);
		} 
		catch (BadLocationException | CoreException | MalformedTreeException e) 
		{
			IStatus status = new Status(IStatus.ERROR, LambdaStubs.PLUGIN_ID, Messages.error_applying_proposal, e);
			ErrorDialog.openError(null, Messages.error_dialog_title, Messages.error_applying_proposal, status);
		}
	}

	@Override
	public Point getSelection(IDocument document) 
	{
		return null;
	}

	@Override
	public String getAdditionalProposalInfo() 
	{
		return null;
	}

	@Override
	public String getDisplayString() 
	{
		return lambdaDescription;
	}

	@Override
	public Image getImage() 
	{
		return null;
	}

	@Override
	public IContextInformation getContextInformation() 
	{
		return null;
	}
}
