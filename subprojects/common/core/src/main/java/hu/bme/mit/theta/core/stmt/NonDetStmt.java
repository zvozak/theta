package hu.bme.mit.theta.core.stmt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import hu.bme.mit.theta.common.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NonDetStmt implements Stmt {

	private final List<Stmt> stmts;

	private static final int HASH_SEED = 361;
	private static final String STMT_LABEL = "nondet";

	private volatile int hashCode = 0;

	protected NonDetStmt(final List<Stmt> stmts) {
		if (stmts.isEmpty()) this.stmts= ImmutableList.of(SkipStmt.getInstance());
		else this.stmts = stmts;
	}

	public NonDetStmt replaceStmts(Map<Stmt, Stmt> mapping) {
		var newStmts = new ArrayList<Stmt>();
		for (Stmt stmt : stmts) {
			newStmts.add(mapping.getOrDefault(stmt, stmt));
		}
		return NonDetStmt.of(newStmts);
	}

	public static NonDetStmt of(final List<Stmt> stmts) {
		return new NonDetStmt(stmts);
	}

	public List<Stmt> getStmts() {
		return stmts;
	}

	@Override
	public <P, R> R accept(final StmtVisitor<? super P, ? extends R> visitor, final P param) {
		return visitor.visit(this, param);
	}

	@Override
	public int hashCode() {
		int result = hashCode;
		if (result == 0) {
			result = HASH_SEED;
			result = 31 * result + stmts.hashCode();
			hashCode = result;
		}
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		} else if (obj instanceof NonDetStmt) {
			final NonDetStmt that = (NonDetStmt) obj;
			return this.getStmts().equals(that.getStmts());
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		return Utils.lispStringBuilder(STMT_LABEL).addAll(stmts).toString();
	}

}
