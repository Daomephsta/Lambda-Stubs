package io.github.daomephsta.lambda_stubs;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;

public class LambdaStubsJavaCompletionProposalComputer implements IJavaCompletionProposalComputer
{
	private static final ASTParser AST_PARSER = ASTParser.newParser(AST.JLS11);
	
	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext context, IProgressMonitor progressMonitor)
	{
		if (context instanceof JavaContentAssistInvocationContext)
		{
			try
			{
				JavaContentAssistInvocationContext javaContext = (JavaContentAssistInvocationContext) context;
				IJavaProject javaProject = javaContext.getCompilationUnit().getJavaProject();
				AST_PARSER.setSource(javaContext.getCompilationUnit());
				AST_PARSER.setProject(javaProject);
				AST_PARSER.setResolveBindings(true);
				CompilationUnit astRoot = (CompilationUnit) AST_PARSER.createAST(progressMonitor);
				NodeFinder nodeFinder = new NodeFinder(astRoot, context.getInvocationOffset(), 0);
				//Locate the node the cursor is inside
				ASTNode coveringNode = nodeFinder.getCoveringNode();
				if (coveringNode instanceof SimpleName)
				{
					SimpleName simpleName = (SimpleName) coveringNode;
					//Ignore identifiers that aren't method parameters
					if (coveringNode.getParent() instanceof MethodInvocation)
					{
						MethodInvocation methodInvocation = (MethodInvocation) coveringNode.getParent();
						//Map the identifier to the type of the parameter it's being passed as
						StructuralPropertyDescriptor locationInParent = simpleName.getLocationInParent();
						if (!locationInParent.isChildListProperty())
							return Collections.emptyList();
						@SuppressWarnings("unchecked")
						int parameterIndex = ((List<ASTNode>) methodInvocation.getStructuralProperty(locationInParent)).indexOf(simpleName);
						ITypeBinding lambdaType = methodInvocation.resolveMethodBinding().getParameterTypes()[parameterIndex];
						return LambdaSpecification.computeValidSpecifications(lambdaType).stream()
								.map(options -> new LambdaStubCompletionProposal(astRoot, coveringNode, options))
								.collect(Collectors.toList());
					}
				}
			}
			catch (JavaModelException e)
			{
				IStatus status = new Status(IStatus.ERROR, LambdaStubs.PLUGIN_ID, Messages.error_computing_proposal, e);
				ErrorDialog.openError(null, Messages.error_dialog_title, Messages.error_computing_proposal, status);
			}
		}
		return Collections.emptyList();
	}

	@Override
	public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext context, IProgressMonitor progressMonitor)
	{
		return Collections.emptyList();
	}

	@Override
	public String getErrorMessage()
	{
		return null;
	}

	@Override
	public void sessionEnded() {}

	@Override
	public void sessionStarted() {}
}
