package com.shapesecurity.shift.transformation;

import static com.shapesecurity.shift.path.StaticBranch.BINDING;
import static com.shapesecurity.shift.path.StaticBranch.BODY;
import static com.shapesecurity.shift.path.StaticBranch.DECLARATION;
import static com.shapesecurity.shift.path.StaticBranch.DECLARATORS;
import static com.shapesecurity.shift.path.StaticBranch.IDENTIFIER;
import static com.shapesecurity.shift.path.StaticBranch.NAME;
import static com.shapesecurity.shift.path.StaticBranch.STATEMENTS;
import static junit.framework.TestCase.assertEquals;

import com.shapesecurity.functional.data.List;
import com.shapesecurity.functional.data.NonEmptyList;
import com.shapesecurity.shift.ast.Identifier;
import com.shapesecurity.shift.ast.Node;
import com.shapesecurity.shift.ast.Script;
import com.shapesecurity.shift.codegen.CodeGen;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;
import com.shapesecurity.shift.path.Branch;
import com.shapesecurity.shift.path.IndexedBranch;


import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class ProjectionTreeTest {
	private static List<Branch> path(Object... parts) {
		return then(List.<Branch>nil(), parts);
	}

	private static List<Branch> then(List<Branch> path, Object... parts) {
		for (Object part : parts) {
			if (part instanceof Branch) {
				path = path.cons((Branch) part);
			} else if (part instanceof Integer) {
				path = path.cons(IndexedBranch.from((Integer) part));
			} else {
				throw new RuntimeException("Illegal branch.");
			}
		}
		return path;
	}

	private ProjectionTree<Identifier> get(@NotNull Script p, @NotNull List<Branch> path) {
		return ProjectionTree.create((Identifier) path.foldRight((Branch b, Node node) -> node.get(b).just(), p), path);
	}


	@Test
	public void testGet() throws JsError {
		List<Branch> parent = path(BODY, STATEMENTS, 0);
		List<Branch> body = then(parent, BODY, STATEMENTS, 0, DECLARATION);
		List<Branch> id = parent.cons(IDENTIFIER);
		NonEmptyList<Branch> cons = body.cons(DECLARATORS);
		List<Branch> path1 = then(cons, 0, BINDING);
		List<Branch> path2 = then(cons, 1, BINDING);
		ProjectionTree<Identifier> t0 = ProjectionTree.create(new Identifier("a"), id);
		ProjectionTree<Identifier> t1 = ProjectionTree.create(new Identifier("b"), path1);
		ProjectionTree<Identifier> t2 = ProjectionTree.create(new Identifier("c"), path2);
		ProjectionTree<Identifier> t = t0.append(t1).append(t2);
		assertEquals("a", t.get(path(BODY, STATEMENTS, 0, IDENTIFIER)).just().name);
		assertEquals("b", t.get(path(BODY, STATEMENTS, 0, BODY, STATEMENTS, 0, DECLARATION, DECLARATORS, 0, BINDING))
			.just().name);
		assertEquals("c", t.get(path(BODY, STATEMENTS, 0, BODY, STATEMENTS, 0, DECLARATION, DECLARATORS, 1, BINDING))
			.just().name);
	}

	@Test
	public void testSimple() throws JsError {
		Script program = Parser.parse("var v1;");
		List<Branch> path = path(BODY, STATEMENTS, 0, DECLARATION, DECLARATORS, 0, BINDING);
		ProjectionTree<Identifier> t = get(program, path).map(i -> new Identifier("a"));
		Script newProgram = ProjectionTree.transform(t, program);
		assertEquals("var a", CodeGen.codeGen(newProgram));
	}

	@Test
	public void testDouble() throws JsError {
		Script program = Parser.parse("var v1, v1;");
		List<Branch> parent = path(BODY, STATEMENTS, 0, DECLARATION);
		NonEmptyList<Branch> decs = parent.cons(DECLARATORS);
		List<Branch> path1 = then(decs, 0, BINDING);
		List<Branch> path2 = then(decs, 1, BINDING);
		ProjectionTree<Identifier> t1 = get(program, path1).map(i -> new Identifier("a"));
		ProjectionTree<Identifier> t2 = get(program, path2).map(i -> new Identifier("b"));
		ProjectionTree<Identifier> t = t1.append(t2);
		Script newProgram = ProjectionTree.transform(t, program);
		assertEquals("var a,b", CodeGen.codeGen(newProgram));
	}

	@Test
	public void testDeep() throws JsError {
		Script program = Parser.parse("function x() { var v1, v1; }");
		List<Branch> parent = path(BODY, STATEMENTS, 0);
		List<Branch> body = then(parent, BODY, STATEMENTS, 0, DECLARATION);
		List<Branch> id = parent.cons(NAME);
		NonEmptyList<Branch> decs = body.cons(DECLARATORS);
		List<Branch> path1 = then(decs, 0, BINDING);
		List<Branch> path2 = then(decs, 1, BINDING);
		ProjectionTree<Identifier> t0 = get(program, id).map(i -> new Identifier("a"));
		ProjectionTree<Identifier> t1 = get(program, path1).map(i -> new Identifier("b"));
		ProjectionTree<Identifier> t2 = get(program, path2).map(i -> new Identifier("c"));
		ProjectionTree<Identifier> t = t0.append(t1).append(t2);
		Script newProgram = ProjectionTree.transform(t, program);
		assertEquals("function a(){var b,c}", CodeGen.codeGen(newProgram));
	}
}
