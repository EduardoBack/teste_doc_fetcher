package net.sourceforge.docfetcher.model.index.outlook;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.docfetcher.base.Util;
import net.sourceforge.docfetcher.base.annotations.MutableCopy;
import net.sourceforge.docfetcher.base.annotations.NotNull;
import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.Fields;
import net.sourceforge.docfetcher.model.TreeNode;
import net.sourceforge.docfetcher.model.index.IndexWriterAdapter;
import net.sourceforge.docfetcher.model.index.IndexingConfig;
import net.sourceforge.docfetcher.model.index.IndexingException;
import net.sourceforge.docfetcher.model.index.IndexingReporter;
import net.sourceforge.docfetcher.model.index.IndexingReporter.ErrorType;
import net.sourceforge.docfetcher.model.index.IndexingReporter.InfoType;
import net.sourceforge.docfetcher.model.parse.ParseException;
import net.sourceforge.docfetcher.model.parse.ParseResult;
import net.sourceforge.docfetcher.model.parse.ParseService;

import org.apache.lucene.document.Document;

import com.pff.PSTMessage;
import com.pff.PSTRecipient;

final class OutlookContext {
	
	@NotNull private final IndexingConfig config;
	@NotNull private final IndexWriterAdapter writer;
	@NotNull private final IndexingReporter reporter;
	@NotNull private final Cancelable cancelable;

	public OutlookContext(	@NotNull IndexingConfig config,
	                      	@NotNull IndexWriterAdapter writer,
							@NotNull IndexingReporter reporter,
							@NotNull Cancelable cancelable) {
		Util.checkNotNull(config, writer, reporter, cancelable);
		this.config = config;
		this.writer = writer;
		this.reporter = reporter;
		this.cancelable = cancelable;
	}
	
	public final boolean isStopped() {
		return cancelable.isCanceled();
	}
	
	// Will handle attachments
	public void index(	@NotNull MailDocument doc,
						@NotNull PSTMessage email,
						boolean added) throws IndexingException {
		reporter.info(InfoType.EXTRACTING, doc);
		try {
			Document luceneDoc = createLuceneDoc(doc, email);
			if (added)
				writer.add(luceneDoc);
			else
				writer.update(doc.getUniqueId(), luceneDoc);
		} catch (IOException e) {
			throw new IndexingException(e);
		}
	}
	
	public void deleteFromIndex(@NotNull String uid) throws IndexingException {
		try {
			writer.delete(uid);
		} catch (IOException e) {
			throw new IndexingException(e);
		}
	}
	
	@NotNull
	private Document createLuceneDoc(	@NotNull final MailDocument doc,
										@NotNull final PSTMessage email) {
		final Document luceneDoc = new Document();
		String subject = email.getSubject();
		String body = email.getBody();
		String sender = getSender(email);
		String recipients = Util.join(", ", getRecipients(email));
		long date = email.getMessageDeliveryTime().getTime();
		long size = body.length(); // assume every char takes up one byte
		
		luceneDoc.add(Fields.UID.create(doc.getUniqueId()));
		luceneDoc.add(Fields.SUBJECT.create(subject));
		luceneDoc.add(Fields.TYPE.create("outlook")); //$NON-NLS-1$
		luceneDoc.add(Fields.SENDER.create(sender));
		luceneDoc.add(Fields.RECIPIENTS.create(recipients));
		luceneDoc.add(Fields.DATE.create(String.valueOf(date)));
		luceneDoc.add(Fields.SIZE.create(size));
		luceneDoc.add(Fields.PARSER.create(Fields.EMAIL_PARSER));
		// TODO Store a more informative path to the email for display on the result panel
		// TODO Fill in more fields as necessary
		
		StringBuilder contents = new StringBuilder();
		contents.append(subject).append(" ");
		contents.append(sender).append(" ");
		contents.append(recipients).append(" ");
		contents.append(body).append(" ");
		// TODO Fill in more fields as necessary
		
		luceneDoc.add(Fields.createContent(contents));
		
		// Parse and append attachments
		new AttachmentVisitor(config, email, true) {
			protected void handleAttachment(String filename,
											File tempFile)
					throws ParseException {
				// TODO Don't try to parse all files -> call ParseService.canParse
				// TODO later: Maybe recurse into archive attachments
				ParseResult parseResult = ParseService.parse(
					config, filename, tempFile, cancelable);
				luceneDoc.add(Fields.createContent(parseResult.getContent()));
				StringBuilder metadata = parseResult.getMetadata();
				metadata.append(filename);
				luceneDoc.add(Fields.createContent(metadata));
			}
			protected void handleException(	String filename,
											Exception e) {
				final String filepath = Util.joinPath(doc.getPath(), filename);
				@SuppressWarnings("serial")
				TreeNode attachNode = new TreeNode(filename, filename) {
					public String getPath() {
						return filepath;
					}
				};
				reporter.fail(ErrorType.ATTACHMENT, attachNode, e);
			}
		}.run();
		
		return luceneDoc;
	}
	
	@NotNull
	static String getSender(@NotNull PSTMessage email) {
		String senderName = email.getSenderName();
		String senderEmailAddress = email.getSenderEmailAddress();
		if (senderName.equals(senderEmailAddress))
			return senderEmailAddress;
		return senderName + " <" + senderEmailAddress + ">";
	}
	
	@MutableCopy
	@NotNull
	static List<String> getRecipients(@NotNull PSTMessage email) {
		try {
			int n = email.getNumberOfRecipients();
			if (n == 0)
				return new ArrayList<String>(0);
			List<String> rs = new ArrayList<String>(n);
			for (int i = 0; i < n; i++) {
				PSTRecipient r = email.getRecipient(i);
				String name = r.getDisplayName();
				String address = r.getEmailAddress();
				if (name.equals(address))
					rs.add(address);
				else
					rs.add(name + " <" + address + ">");
			}
			return rs;
		} catch (Exception e) {
			return new ArrayList<String>(0);
		}
	}

}